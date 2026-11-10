package kr.dothome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

enum NotificationKind {
    EDIT_REQUESTED(NotificationGroup.EDIT), EDIT_APPROVED(NotificationGroup.EDIT),
    EDIT_REJECTED(NotificationGroup.EDIT), EDIT_REVOKED(NotificationGroup.EDIT),
    FAMILY_TASK(NotificationGroup.TASK), FAMILY_NOTICE(NotificationGroup.NOTICE), ARRIVED_HOME(NotificationGroup.ARRIVAL);

    final NotificationGroup group;
    NotificationKind(NotificationGroup group){this.group=group;}
}

@Entity @Table(name="household_notification")
class HouseholdNotification extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String recipientMemberId;
    @Column(nullable=false,length=36) String actorMemberId;
    @Column(nullable=false,length=36) String sourceId;
    @Column(nullable=false,length=24) @Enumerated(EnumType.STRING) NotificationKind kind;
    @Column(nullable=false) Instant createdAt;
    Instant readAt;
}

interface HouseholdNotifications extends JpaRepository<HouseholdNotification,String> {
    @Query("""
        select n from HouseholdNotification n where n.recipientMemberId=:recipient and n.createdAt>:cutoff
        and (:first=true or n.createdAt<:before or (n.createdAt=:before and n.id<:beforeId))
        order by n.createdAt desc,n.id desc
        """)
    List<HouseholdNotification> page(String recipient,Instant cutoff,boolean first,Instant before,String beforeId,Pageable limit);

    @Query("""
        select n.householdId from HouseholdNotification n where n.id=:id and exists
        (select m.id from Member m where m.id=n.recipientMemberId and m.userId=:user and m.active=true)
        """)
    Optional<String> findOwnedHouse(String id,String user);

    long countByRecipientMemberIdAndReadAtIsNullAndCreatedAtAfter(String recipient,Instant cutoff);

    @Modifying @Query("delete from HouseholdNotification n where n.createdAt<=:cutoff")
    void purge(Instant cutoff);
}

record NotificationCreated(HouseholdNotification notification) {}
record NotificationView(String id,NotificationKind kind,String actorMemberId,Instant createdAt,Instant readAt) {}
record NotificationPage(List<NotificationView> items,String nextCursor,long unreadCount) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class HouseholdNotificationService {
    private static final Duration RETENTION=Duration.ofDays(30);
    private final HomeService homes;
    private final HouseholdNotifications notifications;
    private final NotificationPreferenceService preferences;
    private final Members members;
    private final ApplicationEventPublisher publisher;

    HouseholdNotificationService(HomeService homes,HouseholdNotifications notifications,NotificationPreferenceService preferences,Members members,ApplicationEventPublisher publisher){
        this.homes=homes;this.notifications=notifications;this.preferences=preferences;this.members=members;this.publisher=publisher;
    }

    void add(Household house,String recipient,String actor,NotificationKind kind,String source,Instant now){
        if(recipient.equals(actor)||!preferences.allowed(recipient,kind.group))return;
        var row=new HouseholdNotification();
        row.householdId=house.id;
        row.recipientMemberId=recipient;
        row.actorMemberId=actor;
        row.kind=kind;
        row.sourceId=source;
        row.createdAt=now;
        notifications.save(row);
        publisher.publishEvent(new NotificationCreated(row));
    }

    void arrival(Household house,Member actor,String eventId,Instant now){
        for(var recipient:members.findByHouseholdIdAndActiveTrueOrderById(house.id))
            add(house,recipient.id,actor.id,NotificationKind.ARRIVED_HOME,eventId,now);
    }

    NotificationPage list(String h,String user,String cursor,int limit){
        if(limit<1||limit>50)throw ApiException.invalid("알림은 한 번에 1~50개까지 볼 수 있어요.");
        homes.readLock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var cutoff=now.minus(RETENTION);
        var before=cursor==null?null:notifications.findById(cursor)
            .filter(n->n.recipientMemberId.equals(member.id)&&n.createdAt.isAfter(cutoff))
            .orElseThrow(()->ApiException.invalid("알림 목록을 처음부터 다시 불러와 주세요."));
        var rows=notifications.page(member.id,cutoff,before==null,before==null?now:before.createdAt,
            before==null?"":before.id,Pageable.ofSize(limit+1));
        var next=rows.size()>limit?rows.get(limit-1).id:null;
        return new NotificationPage(rows.stream().limit(limit).map(HouseholdNotificationService::view).toList(),next,
            notifications.countByRecipientMemberIdAndReadAtIsNullAndCreatedAtAfter(member.id,cutoff));
    }

    NotificationView read(String id,String user){
        var h=notifications.findOwnedHouse(id,user).orElseThrow(ApiException::missing);
        homes.readLock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var row=notifications.findById(id)
            .filter(n->n.recipientMemberId.equals(member.id)&&n.createdAt.isAfter(now.minus(RETENTION)))
            .orElseThrow(ApiException::missing);
        if(row.readAt==null)row.readAt=now;
        return view(row);
    }

    @Scheduled(fixedDelay=900_000)
    void purgeExpired(){notifications.purge(now().minus(RETENTION).minus(Duration.ofHours(1)));}

    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static NotificationView view(HouseholdNotification row){
        return new NotificationView(row.id,row.kind,row.actorMemberId,row.createdAt,row.readAt);
    }
}

@RestController @RequestMapping("/api/notifications")
class HouseholdNotificationController {
    private final HouseholdNotificationService notifications;
    HouseholdNotificationController(HouseholdNotificationService notifications){this.notifications=notifications;}

    @GetMapping NotificationPage list(@RequestParam String householdId,@RequestParam(required=false) String cursor,
        @RequestParam(defaultValue="15") int limit,Principal user){
        return notifications.list(householdId,user.getName(),cursor,limit);
    }
    @PostMapping("/{id}/read") NotificationView read(@PathVariable String id,Principal user){
        return notifications.read(id,user.getName());
    }
}
