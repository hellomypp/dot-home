package kr.dothome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Entity @Table(name="space_edit_session")
class SpaceEditSession extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String memberId;
    @Column(nullable=false,length=80) String startRequestId;
    @Column(length=36) String recoveryId;
    @Column(length=36) String approvalId;
    long baseDesignVersion;
    long draftVersion;
    @Column(nullable=false,length=1024) String draftJson;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant leaseUntil;
    @Column(nullable=false) Instant expiresAt;
    Instant endedAt;
    @Column(length=80) String commitRequestId;
    Long committedVersion;

    boolean active(Instant now){return endedAt==null && leaseUntil.isAfter(now);}
    boolean retained(Instant now){
        return (endedAt==null?leaseUntil:endedAt).plus(Duration.ofHours(24)).isAfter(now);
    }
    SpaceView draft(){return Spaces.JSON.readValue(draftJson,SpaceView.class);}
}

interface SpaceEdits extends JpaRepository<SpaceEditSession,String> {
    Optional<SpaceEditSession> findByMemberIdAndStartRequestId(String memberId,String requestId);
    Optional<SpaceEditSession> findFirstByMemberIdOrderByCreatedAtDescIdDesc(String memberId);
    Optional<SpaceEditSession> findFirstByHouseholdIdAndEndedAtIsNullAndLeaseUntilAfter(String house,Instant now);

    @Modifying
    @Query("delete from SpaceEditSession s where (s.endedAt is null and s.leaseUntil<=:cutoff) or s.endedAt<=:cutoff")
    void purge(Instant cutoff);
}

record SpaceEditStart(@NotBlank @Size(max=80) String requestId,@Size(max=36) String recoverId,@Size(max=36) String approvalId) {
    SpaceEditStart(String requestId,String recoverId){this(requestId,recoverId,null);}
}
record SpaceEditDraft(@NotNull @PositiveOrZero Long draftVersion,@NotNull @Valid SpaceInput design) {}
record SpaceEditCommit(@NotBlank @Size(max=80) String requestId,
    @NotNull @PositiveOrZero Long baseDesignVersion,@NotNull @PositiveOrZero Long draftVersion) {}
record SpaceEditView(String id,String status,SpaceView draft,long draftVersion,Instant leaseUntil,
    Instant expiresAt,Long committedVersion,String approvalId) {}
record SpaceEditReceipt(long committedVersion,Api.World world) {}
record SpaceEditStatus(SpaceEditView session) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class SpaceEditing {
    private final HomeService homes;
    private final SpaceEdits edits;
    private final SpaceEditAccess access;

    SpaceEditing(HomeService homes,SpaceEdits edits,SpaceEditAccess access){this.homes=homes;this.edits=edits;this.access=access;}

    SpaceEditStatus current(String h,String user){
        var house=homes.readLock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var row=edits.findFirstByMemberIdOrderByCreatedAtDescIdDesc(member.id)
            .filter(s->s.retained(now)).orElse(null);
        return new SpaceEditStatus(row==null?null:view(house,row,now));
    }

    SpaceEditView start(String h,String user,SpaceEditStart input){
        var house=homes.lock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var repeated=edits.findByMemberIdAndStartRequestId(member.id,input.requestId()).orElse(null);
        if(repeated!=null){
            if(!Objects.equals(repeated.recoveryId,input.recoverId()) || !Objects.equals(repeated.approvalId,input.approvalId()))
                throw ApiException.conflict("이미 사용한 편집 시작 요청이에요.");
            if(!repeated.retained(now))throw ApiException.missing();
            return view(house,repeated,now);
        }
        var grant=access.starting(house,member,input.approvalId(),now);
        if(edits.findFirstByHouseholdIdAndEndedAtIsNullAndLeaseUntilAfter(h,now).isPresent())throw busy();
        var current=Spaces.view(house);
        var draft=current;
        if(input.recoverId()!=null){
            var old=owned(h,member.id,input.recoverId(),now);
            if(!"RECOVERABLE".equals(status(house,old,now)))
                throw ApiException.conflict("이 초안을 이어갈 수 없어요. 최신 집 분위기로 다시 시작해 주세요.");
            draft=old.draft();
        }
        SpaceEditAccess.checkScope(grant,current,draft);
        var row=new SpaceEditSession();
        row.householdId=h;
        row.memberId=member.id;
        row.startRequestId=input.requestId();
        row.recoveryId=input.recoverId();
        row.approvalId=input.approvalId();
        row.baseDesignVersion=house.designVersion;
        row.draftVersion=input.recoverId()==null?0:1;
        row.draftJson=Spaces.JSON.writeValueAsString(draft);
        row.createdAt=now;
        row.expiresAt=now.plusSeconds(1200);
        renew(row,now);
        if(grant!=null)access.changed(house);
        return view(house,edits.save(row),now);
    }

    SpaceEditView draft(String h,String user,String id,SpaceEditDraft input){
        var house=homes.lock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var row=owned(h,member.id,id,now);
        requireActive(row,now);
        var grant=access.authorized(house,member,row);
        var design=input.design();
        if(row.baseDesignVersion!=design.version() || house.designVersion!=row.baseDesignVersion)
            throw ApiException.conflict("집 분위기가 바뀌었어요. 최신 설정으로 다시 시작해 주세요.");
        var next=Spaces.proposed(house,design);
        SpaceEditAccess.checkScope(grant,Spaces.view(house),next);
        if(!next.equals(row.draft())){
            if(row.draftVersion!=input.draftVersion())throw ApiException.conflict("다른 기기에서 초안을 바꿨어요.");
            row.draftJson=Spaces.JSON.writeValueAsString(next);
            row.draftVersion++;
        }
        renew(row,now);
        return view(house,row,now);
    }

    SpaceEditView heartbeat(String h,String user,String id){
        var house=homes.lock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var row=owned(h,member.id,id,now);
        requireActive(row,now);
        access.authorized(house,member,row);
        renew(row,now);
        return view(house,row,now);
    }

    SpaceEditReceipt commit(String h,String user,String id,SpaceEditCommit input){
        var house=homes.readLock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var row=owned(h,member.id,id,now);
        if(row.committedVersion!=null){
            if(input.requestId().equals(row.commitRequestId)
                && input.baseDesignVersion()==row.baseDesignVersion && input.draftVersion()==row.draftVersion)
                return new SpaceEditReceipt(row.committedVersion,homes.snapshot(house,member.id));
            throw ApiException.conflict("이미 저장한 편집이에요.");
        }
        HomeService.writable(house);
        requireActive(row,now);
        var grant=access.authorized(house,member,row);
        if(row.baseDesignVersion!=input.baseDesignVersion() || row.draftVersion!=input.draftVersion())
            throw ApiException.conflict("편집 내용이 바뀌었어요. 최신 초안을 확인해 주세요.");
        var draft=row.draft();
        SpaceEditAccess.checkScope(grant,Spaces.view(house),draft);
        var world=homes.saveDesign(house,member.id,new SpaceInput(draft.presetId(),draft.version(),draft.variants(),draft.positions()));
        row.endedAt=now;
        row.commitRequestId=input.requestId();
        row.committedVersion=world.design().version();
        access.endGrant(row,SpaceEditRequestState.USED);
        return new SpaceEditReceipt(row.committedVersion,world);
    }

    SpaceEditReceipt result(String h,String user,String id,String requestId){
        var house=homes.readLock(h,user);
        var member=homes.requireMember(h,user);
        var row=owned(h,member.id,id,now());
        if(row.committedVersion==null || !requestId.equals(row.commitRequestId))throw ApiException.missing();
        return new SpaceEditReceipt(row.committedVersion,homes.snapshot(house,member.id));
    }

    SpaceEditView cancel(String h,String user,String id){
        var house=homes.readLock(h,user);
        var member=homes.requireMember(h,user);
        var now=now();
        var row=owned(h,member.id,id,now);
        if(row.active(now)){
            access.authorized(house,member,row);
            finish(house,row,now);
        }
        return view(house,row,now);
    }

    void revoke(String h,String user,String id){
        var house=homes.lock(h,user);
        homes.owner(house,user);
        var row=edits.findById(id).filter(s->s.householdId.equals(h)&&s.approvalId!=null)
            .orElseThrow(ApiException::missing);
        var now=now();
        if(row.active(now))finish(house,row,now);
    }

    private void finish(Household house,SpaceEditSession row,Instant now){
        row.endedAt=now;
        if(row.approvalId!=null){
            access.endGrant(row,SpaceEditRequestState.CANCELLED);
            access.changed(house);
        }
    }

    @Scheduled(fixedDelay=300_000)
    void purgeExpired(){edits.purge(now().minus(Duration.ofHours(24)));}

    private SpaceEditSession owned(String h,String memberId,String id,Instant now){
        var row=edits.findById(id).filter(s->s.householdId.equals(h)&&s.memberId.equals(memberId))
            .orElseThrow(ApiException::missing);
        if(!row.retained(now))throw ApiException.missing();
        return row;
    }

    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static void renew(SpaceEditSession row,Instant now){
        var lease=now.plusSeconds(90);
        row.leaseUntil=lease.isBefore(row.expiresAt)?lease:row.expiresAt;
    }
    private static void requireActive(SpaceEditSession row,Instant now){
        if(!row.active(now))throw new ApiException(HttpStatus.GONE,"EDIT_EXPIRED","편집 시간이 끝났어요. 초안을 다시 열어 주세요.");
    }
    static ApiException busy(){
        return new ApiException(HttpStatus.CONFLICT,"EDIT_BUSY","다른 화면에서 집을 편집하고 있어요. 열려 있는 초안을 확인해 주세요.");
    }
    private static String status(Household house,SpaceEditSession row,Instant now){
        if(row.committedVersion!=null)return "COMMITTED";
        if(row.active(now) && house.pendingDeleteUntil==null)return "ACTIVE";
        if(row.draftVersion>0 && row.baseDesignVersion==house.designVersion)return "RECOVERABLE";
        return "ENDED";
    }
    private static SpaceEditView view(Household house,SpaceEditSession row,Instant now){
        return new SpaceEditView(row.id,status(house,row,now),row.draft(),row.draftVersion,row.leaseUntil,row.expiresAt,row.committedVersion,row.approvalId);
    }
}

@RestController @RequestMapping("/api/households/{h}/edit-sessions")
class SpaceEditController {
    private final SpaceEditing edits;
    SpaceEditController(SpaceEditing edits){this.edits=edits;}

    @GetMapping SpaceEditStatus current(@PathVariable String h,Principal user){return edits.current(h,user.getName());}
    @PostMapping SpaceEditView start(@PathVariable String h,Principal user,@Valid @RequestBody SpaceEditStart input){
        return edits.start(h,user.getName(),input);
    }
    @PatchMapping("/{id}/draft") SpaceEditView draft(@PathVariable String h,@PathVariable String id,Principal user,@Valid @RequestBody SpaceEditDraft input){
        return edits.draft(h,user.getName(),id,input);
    }
    @PostMapping("/{id}/heartbeat") SpaceEditView heartbeat(@PathVariable String h,@PathVariable String id,Principal user){
        return edits.heartbeat(h,user.getName(),id);
    }
    @PostMapping("/{id}/commit") SpaceEditReceipt commit(@PathVariable String h,@PathVariable String id,Principal user,@Valid @RequestBody SpaceEditCommit input){
        return edits.commit(h,user.getName(),id,input);
    }
    @GetMapping("/{id}/result") SpaceEditReceipt result(@PathVariable String h,@PathVariable String id,Principal user,@RequestParam String requestId){
        return edits.result(h,user.getName(),id,requestId);
    }
    @PostMapping("/{id}/cancel") SpaceEditView cancel(@PathVariable String h,@PathVariable String id,Principal user){
        return edits.cancel(h,user.getName(),id);
    }
    @PostMapping("/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable String h,@PathVariable String id,Principal user){edits.revoke(h,user.getName(),id);}
}
