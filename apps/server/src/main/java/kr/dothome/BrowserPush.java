package kr.dothome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

@Entity @Table(name="browser_push_subscription")
class PushSubscription extends Row {
    @Column(nullable=false,length=36) String userId;
    @Column(nullable=false,length=64,unique=true) String endpointHash;
    @Column(nullable=false,length=2048) String endpoint;
    @Column(nullable=false,length=64) String keyFingerprint;
    @Column(nullable=false,length=64) String sessionHash;
}

@Entity @Table(name="browser_push_delivery")
class PushDelivery extends Row {
    @Column(nullable=false,length=36) String notificationId;
    @Column(nullable=false,length=36) String subscriptionId;
    @Column(nullable=false) Instant nextAttemptAt;
    @Column(nullable=false) Instant expiresAt;
    int attempts;
}

interface PushSubscriptions extends JpaRepository<PushSubscription,String> {
    Optional<PushSubscription> findByEndpointHash(String hash);
    List<PushSubscription> findByUserIdAndKeyFingerprint(String userId,String fingerprint);
    long countByUserId(String userId);
    void deleteByUserIdAndSessionHash(String userId,String sessionHash);
}

interface PushDeliveries extends JpaRepository<PushDelivery,String> {
    @Query("select d.id from PushDelivery d where d.nextAttemptAt<=:now order by d.nextAttemptAt,d.id")
    List<String> due(Instant now,Pageable limit);

    @Query("""
        select n.householdId from PushDelivery d, HouseholdNotification n
        where d.id=:id and n.id=d.notificationId
        """)
    Optional<String> household(String id);

    @Modifying @Query("delete from PushDelivery d where d.expiresAt<=:now")
    void purge(Instant now);
}

record PushSettings(boolean available,String publicKey) {}
record PushEndpoint(@NotBlank @Size(max=2048) String endpoint) {}
record PushStatus(boolean enabled) {}
record PreparedPush(String deliveryId,String endpoint,String topic,int ttl) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class PushRegistration {
    private final PushTransport transport;
    private final PushSubscriptions subscriptions;
    private final Accounts accounts;

    PushRegistration(PushTransport transport,PushSubscriptions subscriptions,Accounts accounts){
        this.transport=transport;this.subscriptions=subscriptions;this.accounts=accounts;
    }

    PushSettings settings(){return new PushSettings(transport.enabled(),transport.enabled()?transport.publicKey():null);}

    PushStatus status(String user,String endpoint,String sessionId){
        accounts.locked(user).orElseThrow(ApiException::forbidden);
        var existing=transport.enabled()?subscriptions.findByEndpointHash(HomeService.hash(endpoint))
            .filter(row->row.userId.equals(user)&&row.keyFingerprint.equals(transport.fingerprint())).orElse(null):null;
        if(existing!=null)existing.sessionHash=HomeService.hash(sessionId);
        return new PushStatus(existing!=null);
    }

    void register(String user,String endpoint,String sessionId){
        if(!transport.enabled())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PUSH_UNAVAILABLE","이 서버에서는 웹 푸시를 준비 중이에요.");
        String target=PushTransport.endpoint(endpoint).toString();
        accounts.locked(user).orElseThrow(ApiException::forbidden);
        String hash=HomeService.hash(target);
        var existing=subscriptions.findByEndpointHash(hash).orElse(null);
        if(existing!=null){
            if(!existing.userId.equals(user))throw owned();
            if(existing.keyFingerprint.equals(transport.fingerprint())){
                existing.sessionHash=HomeService.hash(sessionId);
                return;
            }
            throw ApiException.conflict("이 브라우저의 기존 알림을 해제한 뒤 다시 연결해 주세요.");
        }
        if(subscriptions.countByUserId(user)>=8)
            throw ApiException.conflict("등록된 알림 기기가 많아요. 기존 기기의 알림을 끈 뒤 다시 시도해 주세요.");
        var row=new PushSubscription();
        row.userId=user;row.endpointHash=hash;row.endpoint=target;row.keyFingerprint=transport.fingerprint();
        row.sessionHash=HomeService.hash(sessionId);
        try{subscriptions.saveAndFlush(row);}
        catch(DataIntegrityViolationException ex){throw owned();}
    }

    void disable(String user,String endpoint){
        accounts.locked(user).orElseThrow(ApiException::forbidden);
        subscriptions.findByEndpointHash(HomeService.hash(endpoint)).filter(row->row.userId.equals(user))
            .ifPresent(subscriptions::delete);
    }

    void logout(String user,String sessionId){
        accounts.locked(user).orElseThrow(ApiException::forbidden);
        subscriptions.deleteByUserIdAndSessionHash(user,HomeService.hash(sessionId));
    }

    private static ApiException owned(){
        return new ApiException(HttpStatus.CONFLICT,"PUSH_SUBSCRIPTION_OWNED","이 브라우저에 이전 알림 구독이 있어요. 새 구독으로 바꿔 주세요.");
    }
}

@RestController @RequestMapping("/api/me/browser-push")
class BrowserPushController {
    private final PushRegistration registration;
    BrowserPushController(PushRegistration registration){this.registration=registration;}

    @GetMapping PushSettings settings(){return registration.settings();}

    @PostMapping("/status") PushStatus status(Principal user,@Valid @RequestBody PushEndpoint input,HttpServletRequest request){
        return registration.status(user.getName(),input.endpoint(),sessionId(request));
    }

    @PutMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(Principal user,@Valid @RequestBody PushEndpoint input,HttpServletRequest request){
        registration.register(user.getName(),input.endpoint(),sessionId(request));
    }

    private static String sessionId(HttpServletRequest request){
        var session=request.getSession(false);
        if(session==null)throw ApiException.forbidden();
        return session.getId();
    }

    @PostMapping("/disable") @ResponseStatus(HttpStatus.NO_CONTENT)
    void disable(Principal user,@Valid @RequestBody PushEndpoint input){registration.disable(user.getName(),input.endpoint());}
}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class BrowserPushOutbox {
    private static final Duration LIFETIME=Duration.ofMinutes(5);
    private final PushTransport transport;
    private final PushSubscriptions subscriptions;
    private final PushDeliveries deliveries;
    private final HouseholdNotifications notifications;
    private final NotificationPreferenceService preferences;
    private final Households houses;
    private final Members members;
    private final SpaceEditRequests edits;
    private final HouseholdRequests requests;
    private final ExpressionStore expressions;
    private final LifeEvents events;
    private final LocationDevices devices;

    BrowserPushOutbox(PushTransport transport,PushSubscriptions subscriptions,PushDeliveries deliveries,
        HouseholdNotifications notifications,NotificationPreferenceService preferences,Households houses,Members members,
        SpaceEditRequests edits,HouseholdRequests requests,ExpressionStore expressions,LifeEvents events,LocationDevices devices){
        this.transport=transport;this.subscriptions=subscriptions;this.deliveries=deliveries;this.notifications=notifications;
        this.preferences=preferences;this.houses=houses;this.members=members;this.edits=edits;this.requests=requests;
        this.expressions=expressions;this.events=events;this.devices=devices;
    }

    @EventListener @Transactional(propagation=Propagation.MANDATORY)
    void enqueue(NotificationCreated created){
        var notice=created.notification();
        if(!transport.enabled()||!preferences.pushAllowed(notice.recipientMemberId,notice.kind.group,notice.createdAt))return;
        var member=active(notice.recipientMemberId,notice.householdId);
        if(member==null)return;
        for(var subscription:subscriptions.findByUserIdAndKeyFingerprint(member.userId,transport.fingerprint())){
            var job=new PushDelivery();
            job.notificationId=notice.id;
            job.subscriptionId=subscription.id;
            job.nextAttemptAt=notice.createdAt;
            job.expiresAt=notice.createdAt.plus(LIFETIME);
            deliveries.save(job);
        }
    }

    List<String> due(Instant now){
        if(!transport.enabled())return List.of();
        deliveries.purge(now);
        return deliveries.due(now,Pageable.ofSize(20));
    }

    PreparedPush prepare(String id,Instant now){
        var house=deliveries.household(id).flatMap(houses::locked).orElse(null);
        var job=deliveries.findById(id).orElse(null);
        if(job==null)return null;
        var notice=notifications.findById(job.notificationId).orElse(null);
        var subscription=subscriptions.findById(job.subscriptionId).orElse(null);
        if(house==null||house.pendingDeleteUntil!=null||!job.expiresAt.isAfter(now)||notice==null||notice.readAt!=null||subscription==null||
            !subscription.keyFingerprint.equals(transport.fingerprint())||!eligible(house,notice,subscription,now)){
            deliveries.delete(job);
            return null;
        }
        int ttl=(int)Duration.between(now,job.expiresAt).toSeconds();
        if(ttl<1){
            deliveries.delete(job);
            return null;
        }
        return new PreparedPush(job.id,subscription.endpoint,notice.id.replace("-",""),ttl);
    }

    void finish(String id,PushResponse response,Instant now){
        var job=deliveries.findById(id).orElse(null);
        if(job==null)return;
        int status=response.status();
        if(status==404||status==410){
            subscriptions.findById(job.subscriptionId).ifPresent(subscriptions::delete);
            return;
        }
        if((status==0||status==429||status>=500)&&++job.attempts<3){
            long seconds=Math.max(job.attempts*15L,Math.min(300,response.retryAfter().getSeconds()));
            var retryAt=now.plusSeconds(seconds);
            if(retryAt.isBefore(job.expiresAt)){
                job.nextAttemptAt=retryAt;
                return;
            }
        }
        deliveries.delete(job);
    }

    private boolean eligible(Household house,HouseholdNotification notice,PushSubscription subscription,Instant now){
        var recipient=active(notice.recipientMemberId,house.id);
        var actor=active(notice.actorMemberId,house.id);
        if(recipient==null||actor==null||!recipient.userId.equals(subscription.userId)||
            !preferences.pushAllowed(recipient.id,notice.kind.group,now))return false;
        return switch(notice.kind){
            case EDIT_REQUESTED,EDIT_APPROVED -> edits.findById(notice.sourceId)
                .filter(row->row.householdId.equals(house.id)).map(row->notice.kind==NotificationKind.EDIT_REQUESTED
                    ?row.stateAt(now,null)==SpaceEditRequestState.PENDING:row.stateAt(now,null)==SpaceEditRequestState.APPROVED).orElse(false);
            case FAMILY_TASK,FAMILY_NOTICE -> requests.findById(notice.sourceId)
                .filter(row->row.householdId.equals(house.id)).map(row->switch(row.stateAt(now)){
                    case ACTIVE, OPEN, CLAIMED -> true;
                    default -> false;
                }).orElse(false);
            case ARRIVED_HOME -> arrivalEligible(actor,notice);
            case EDIT_REJECTED,EDIT_REVOKED -> true;
        };
    }

    private boolean arrivalEligible(Member actor,HouseholdNotification notice){
        var sharing=expressions.sharing(actor,expressions.load(actor));
        if(!sharing.enabled())return false;
        return events.findById(notice.sourceId).filter(event->event.authorMemberId.equals(actor.id))
            .map(event->"MANUAL".equals(event.eventSource)||devices.findById(actor.userId)
                .filter(row->row.matches(actor,sharing)).isPresent()).orElse(false);
    }

    private Member active(String member,String house){
        return members.findById(member).filter(m->m.active&&m.householdId.equals(house)).orElse(null);
    }
}

@Configuration
class PushSchedulerConfiguration {
    @Bean(name="pushTaskScheduler",defaultCandidate=false)
    ThreadPoolTaskScheduler pushTaskScheduler(){
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("web-push-");
        return scheduler;
    }
}

@Service
class BrowserPushWorker {
    private final BrowserPushOutbox outbox;
    private final PushTransport transport;

    BrowserPushWorker(BrowserPushOutbox outbox,PushTransport transport){this.outbox=outbox;this.transport=transport;}

    @Scheduled(scheduler="pushTaskScheduler",initialDelayString="${app.push.poll-delay:PT20S}",fixedDelayString="${app.push.poll-delay:PT20S}")
    void poll(){
        for(var id:outbox.due(now())){
            var push=outbox.prepare(id,now());
            if(push==null)continue;
            PushResponse response;
            try{response=transport.send(push.endpoint(),push.topic(),push.ttl(),now());}
            catch(InterruptedException ex){Thread.currentThread().interrupt();return;}
            catch(IOException ex){response=new PushResponse(0,Duration.ZERO);}
            catch(GeneralSecurityException|ApiException ex){response=new PushResponse(400,Duration.ZERO);}
            outbox.finish(push.deliveryId(),response,now());
        }
    }

    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
}
