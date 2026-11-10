package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

enum SpaceEditScope { PRESET, VARIANTS, LAYOUT, SURFACES }
enum SpaceEditRequestState { PENDING, APPROVED, REJECTED, CANCELLED, USED, EXPIRED }

@Entity @Table(name="space_edit_request")
class SpaceEditRequest extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String memberId;
    @Column(nullable=false,length=80) String requestId;
    @Column(length=80) String decisionRequestId;
    @Column(nullable=false,length=32) String scope;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) SpaceEditRequestState state=SpaceEditRequestState.PENDING;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant expiresAt;
    Instant startBefore;
    long version;

    Set<SpaceEditScope> scopes(){
        var values=EnumSet.noneOf(SpaceEditScope.class);
        for(var item:scope.split(","))values.add(SpaceEditScope.valueOf(item));
        return values;
    }

    boolean open(){return state==SpaceEditRequestState.PENDING || state==SpaceEditRequestState.APPROVED;}
    SpaceEditRequestState stateAt(Instant now,String activeApproval){
        if(state==SpaceEditRequestState.PENDING && !expiresAt.isAfter(now))return SpaceEditRequestState.EXPIRED;
        if(state==SpaceEditRequestState.APPROVED && !startBefore.isAfter(now) && !id.equals(activeApproval))
            return SpaceEditRequestState.EXPIRED;
        return state;
    }

    void setState(SpaceEditRequestState next){
        state=next;
        version++;
    }
}

interface SpaceEditRequests extends JpaRepository<SpaceEditRequest,String> {
    Optional<SpaceEditRequest> findByMemberIdAndRequestId(String memberId,String requestId);
    Optional<SpaceEditRequest> findFirstByMemberIdOrderByCreatedAtDescIdDesc(String memberId);
    List<SpaceEditRequest> findByHouseholdIdAndStateInOrderByCreatedAtDesc(String h,Collection<SpaceEditRequestState> states);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from SpaceEditRequest r where r.createdAt<=:cutoff")
    void purge(Instant cutoff);
}

record AskSpaceEdit(@NotBlank @Size(max=80) String requestId,@NotEmpty Set<@NotNull SpaceEditScope> scope) {}
record DecideSpaceEdit(@NotBlank @Size(max=80) String requestId,@NotNull @PositiveOrZero Long version) {}
record CancelSpaceEdit(@NotNull @PositiveOrZero Long version) {}
record SpaceRequestView(String id,String memberId,Set<SpaceEditScope> scope,SpaceEditRequestState state,
    Instant expiresAt,Instant startBefore,long version) {}
record SpaceEditorPresence(String sessionId,String memberId,Instant expiresAt) {}
record SpaceEditInbox(List<SpaceRequestView> requests,SpaceEditorPresence activeEditor) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class SpaceEditAccess {
    private static final Set<SpaceEditRequestState> OPEN=EnumSet.of(SpaceEditRequestState.PENDING,SpaceEditRequestState.APPROVED);
    private final HomeService homes;
    private final SpaceEdits sessions;
    private final SpaceEditRequests requests;

    SpaceEditAccess(HomeService homes,SpaceEdits sessions,SpaceEditRequests requests){
        this.homes=homes;this.sessions=sessions;this.requests=requests;
    }

    SpaceEditInbox inbox(String h,String user){
        var house=homes.readLock(h,user);
        var actor=homes.requireMember(h,user);
        var now=now();
        var active=active(h,now);
        var grant=active==null?null:active.approvalId;
        boolean owner=house.ownerId.equals(user);
        var visible=owner
            ? open(h).stream().map(r->view(r,now,grant)).filter(r->OPEN.contains(r.state())).toList()
            : requests.findFirstByMemberIdOrderByCreatedAtDescIdDesc(actor.id).stream()
                .filter(r->r.createdAt.isAfter(now.minus(Duration.ofDays(30)))).map(r->view(r,now,grant)).toList();
        return new SpaceEditInbox(visible,owner && active!=null
            ? new SpaceEditorPresence(active.id,active.memberId,active.expiresAt) : null);
    }

    SpaceRequestView ask(String h,String user,AskSpaceEdit input){
        var house=homes.lock(h,user);
        var actor=homes.requireMember(h,user);
        if(house.ownerId.equals(user))throw ApiException.invalid("집 대표는 바로 편집할 수 있어요.");
        var now=now();
        var active=active(h,now);
        var grant=active==null?null:active.approvalId;
        var repeated=requests.findByMemberIdAndRequestId(actor.id,input.requestId()).orElse(null);
        if(repeated!=null){
            if(!repeated.scopes().equals(input.scope()))throw ApiException.conflict("이미 사용한 편집 요청이에요.");
            return view(repeated,now,grant);
        }
        var latest=requests.findFirstByMemberIdOrderByCreatedAtDescIdDesc(actor.id).orElse(null);
        if(latest!=null && OPEN.contains(latest.stateAt(now,grant)))return view(latest,now,grant);
        var row=new SpaceEditRequest();
        row.householdId=h;
        row.memberId=actor.id;
        row.requestId=input.requestId();
        row.scope=String.join(",",input.scope().stream().map(Enum::name).sorted().toList());
        row.createdAt=now;
        row.expiresAt=now.plusSeconds(1800);
        changed(house);
        return view(requests.save(row),now,grant);
    }

    SpaceRequestView decide(String h,String user,String id,DecideSpaceEdit input,boolean approve){
        var house=homes.lock(h,user);
        homes.owner(house,user);
        var row=find(h,id);
        var now=now();
        var next=approve?SpaceEditRequestState.APPROVED:SpaceEditRequestState.REJECTED;
        if(input.requestId().equals(row.decisionRequestId) && row.state==next)return view(row,now,null);
        var state=row.stateAt(now,null);
        if(state==SpaceEditRequestState.EXPIRED)throw expired();
        if(state!=SpaceEditRequestState.PENDING || input.version()!=row.version)
            throw ApiException.conflict("편집 요청이 바뀌었어요. 최신 상태를 확인해 주세요.");
        row.decisionRequestId=input.requestId();
        if(approve)row.startBefore=now.plusSeconds(1800);
        row.setState(next);
        changed(house);
        return view(row,now,null);
    }

    SpaceRequestView cancel(String h,String user,String id,long version){
        var house=homes.lock(h,user);
        var actor=homes.requireMember(h,user);
        var row=find(h,id);
        if(!row.memberId.equals(actor.id) && !house.ownerId.equals(user))throw ApiException.forbidden();
        var now=now();
        var active=active(h,now);
        var grant=active==null?null:active.approvalId;
        if(row.state==SpaceEditRequestState.CANCELLED)return view(row,now,grant);
        if(!OPEN.contains(row.stateAt(now,grant)) || row.version!=version)
            throw ApiException.conflict("편집 요청이 바뀌었어요. 최신 상태를 확인해 주세요.");
        row.setState(SpaceEditRequestState.CANCELLED);
        if(row.id.equals(grant))active.endedAt=now;
        changed(house);
        return view(row,now,null);
    }

    SpaceEditRequest starting(Household house,Member member,String approvalId,Instant now){
        if(house.ownerId.equals(member.userId)){
            if(approvalId!=null)throw ApiException.invalid("집 대표는 승인 없이 편집할 수 있어요.");
            return null;
        }
        if(approvalId==null)throw ApiException.forbidden();
        var row=grantFor(house,member,approvalId);
        if(!row.startBefore.isAfter(now))throw expired();
        return row;
    }

    SpaceEditRequest authorized(Household house,Member member,SpaceEditSession session){
        if(session.approvalId==null){
            homes.owner(house,member.userId);
            return null;
        }
        return grantFor(house,member,session.approvalId);
    }

    private SpaceEditRequest grantFor(Household house,Member member,String id){
        var row=find(house.id,id);
        if(!row.memberId.equals(member.id) || row.state!=SpaceEditRequestState.APPROVED)throw ApiException.forbidden();
        return row;
    }

    void endGrant(SpaceEditSession session,SpaceEditRequestState result){
        if(session.approvalId==null)return;
        var row=find(session.householdId,session.approvalId);
        if(row.state==SpaceEditRequestState.APPROVED)row.setState(result);
    }

    void endForHouse(String h,Instant now){
        open(h).forEach(r->r.setState(SpaceEditRequestState.CANCELLED));
        var active=active(h,now);
        if(active!=null)active.endedAt=now;
    }

    @EventListener
    void membershipEnding(MembershipEnding event){
        var h=event.household().id;
        open(h).stream().filter(r->r.memberId.equals(event.memberId()))
            .forEach(r->r.setState(SpaceEditRequestState.CANCELLED));
        var now=now();
        var active=active(h,now);
        if(active!=null && active.memberId.equals(event.memberId()))active.endedAt=now;
    }

    @Scheduled(fixedDelay=300_000)
    void purgeExpired(){requests.purge(now().minus(Duration.ofDays(30)));}

    static void checkScope(SpaceEditRequest grant,SpaceView current,SpaceView draft){
        if(grant==null)return;
        var scopes=grant.scopes();
        var basePositions=Spaces.sameMap(current.presetId(),draft.presetId())?current.positions():Map.of();
        if(!scopes.contains(SpaceEditScope.PRESET) && !current.presetId().equals(draft.presetId())
            || !scopes.contains(SpaceEditScope.VARIANTS) && !current.variants().equals(draft.variants())
            || !scopes.contains(SpaceEditScope.LAYOUT) && !basePositions.equals(draft.positions())
            || !scopes.contains(SpaceEditScope.SURFACES) && !current.surfaces().equals(draft.surfaces()))
            throw new ApiException(HttpStatus.FORBIDDEN,"EDIT_SCOPE","승인받은 항목만 바꿀 수 있어요.");
    }

    void changed(Household h){homes.emit(h,"SPACE_EDIT_ACCESS_CHANGED",Map.of());}
    private List<SpaceEditRequest> open(String h){return requests.findByHouseholdIdAndStateInOrderByCreatedAtDesc(h,OPEN);}
    private SpaceEditSession active(String h,Instant now){
        return sessions.findFirstByHouseholdIdAndEndedAtIsNullAndLeaseUntilAfter(h,now).orElse(null);
    }
    private SpaceEditRequest find(String h,String id){
        return requests.findById(id).filter(r->r.householdId.equals(h)).orElseThrow(ApiException::missing);
    }
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static ApiException expired(){return new ApiException(HttpStatus.GONE,"EDIT_APPROVAL_EXPIRED","편집 승인 기한이 지났어요. 다시 요청해 주세요.");}
    private static SpaceRequestView view(SpaceEditRequest row,Instant now,String active){
        return new SpaceRequestView(row.id,row.memberId,row.scopes(),row.stateAt(now,active),row.expiresAt,row.startBefore,row.version);
    }
}

@RestController @RequestMapping("/api/households/{h}/edit-requests")
class SpaceEditRequestController {
    private final SpaceEditAccess access;
    SpaceEditRequestController(SpaceEditAccess access){this.access=access;}

    @GetMapping SpaceEditInbox inbox(@PathVariable String h,Principal user){return access.inbox(h,user.getName());}
    @PostMapping SpaceRequestView ask(@PathVariable String h,Principal user,@Valid @RequestBody AskSpaceEdit input){
        return access.ask(h,user.getName(),input);
    }
    @PostMapping("/{id}/approve") SpaceRequestView approve(@PathVariable String h,@PathVariable String id,Principal user,@Valid @RequestBody DecideSpaceEdit input){
        return access.decide(h,user.getName(),id,input,true);
    }
    @PostMapping("/{id}/reject") SpaceRequestView reject(@PathVariable String h,@PathVariable String id,Principal user,@Valid @RequestBody DecideSpaceEdit input){
        return access.decide(h,user.getName(),id,input,false);
    }
    @PostMapping("/{id}/cancel") SpaceRequestView cancel(@PathVariable String h,@PathVariable String id,Principal user,@Valid @RequestBody CancelSpaceEdit input){
        return access.cancel(h,user.getName(),id,input.version());
    }
}
