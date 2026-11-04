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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

enum TransferState { PENDING, ACCEPTED, CANCELLED, EXPIRED }

@Entity @Table(name="ownership_transfer")
class OwnershipTransfer extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String fromMemberId;
    @Column(nullable=false,length=36) String targetMemberId;
    @Column(nullable=false,length=80) String requestId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) TransferState transferState=TransferState.PENDING;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant expiresAt;
    Instant respondedAt;
    @Version long version;

    TransferState stateAt(Instant now){
        return transferState==TransferState.PENDING&&!expiresAt.isAfter(now)?TransferState.EXPIRED:transferState;
    }
}

interface OwnershipTransfers extends JpaRepository<OwnershipTransfer,String> {
    Optional<OwnershipTransfer> findByFromMemberIdAndRequestId(String member,String requestId);
    Optional<OwnershipTransfer> findFirstByHouseholdIdAndTransferStateOrderByCreatedAtDesc(String house,TransferState state);
}

record ProposeOwnership(@NotBlank @Size(max=80) String requestId,@NotBlank @Size(max=36) String targetMemberId) {}
record DecideOwnership(@NotNull @PositiveOrZero Long version,Boolean adultConfirmed) {}
record OwnershipView(String id,String fromMemberId,String targetMemberId,TransferState state,Instant expiresAt,long version) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class OwnershipService {
    private final HomeService homes;
    private final Members members;
    private final OwnershipTransfers transfers;

    OwnershipService(HomeService homes,Members members,OwnershipTransfers transfers){
        this.homes=homes;this.members=members;this.transfers=transfers;
    }

    List<OwnershipView> current(String h,String user){
        var actor=homes.requireMember(h,user);
        return pending(h).filter(transfer->participates(transfer,actor.id))
            .map(transfer->List.of(view(transfer,now()))).orElseGet(List::of);
    }

    OwnershipView propose(String h,String user,ProposeOwnership input){
        var house=homes.lock(h,user);
        homes.owner(house,user);
        var from=homes.requireMember(h,user);
        var now=now();
        var repeated=transfers.findByFromMemberIdAndRequestId(from.id,input.requestId()).orElse(null);
        if(repeated!=null){
            if(!repeated.targetMemberId.equals(input.targetMemberId()))
                throw ApiException.conflict("이미 사용한 이전 요청이에요.");
            return view(repeated,now);
        }
        var target=activeInHouse(h,input.targetMemberId());
        if(target.id.equals(from.id)||!target.role.equals("MEMBER"))
            throw ApiException.invalid("다른 가족을 대표로 지정해 주세요.");
        pending(h).ifPresent(existing->{
            if(existing.stateAt(now)==TransferState.PENDING)
                throw ApiException.conflict("이미 응답을 기다리는 이전 제안이 있어요.");
            existing.transferState=TransferState.EXPIRED;
        });

        var transfer=new OwnershipTransfer();
        transfer.householdId=h;
        transfer.fromMemberId=from.id;
        transfer.targetMemberId=target.id;
        transfer.requestId=input.requestId();
        transfer.createdAt=now;
        transfer.expiresAt=now.plus(Duration.ofHours(24));
        transfers.saveAndFlush(transfer);
        homes.emit(house,"OWNERSHIP_TRANSFER_CHANGED",Map.of());
        return view(transfer,now);
    }

    OwnershipView decide(String h,String user,String id,DecideOwnership input,boolean accept){
        var house=homes.lock(h,user);
        var actor=homes.requireMember(h,user);
        var transfer=transfers.findById(id).filter(row->row.householdId.equals(h)).orElseThrow(ApiException::forbidden);
        if(!participates(transfer,actor.id)||accept&&!transfer.targetMemberId.equals(actor.id))
            throw ApiException.forbidden();
        var now=now();
        var state=transfer.stateAt(now);
        var result=accept?TransferState.ACCEPTED:TransferState.CANCELLED;
        if(state==result)return view(transfer,now);
        if(state==TransferState.EXPIRED)
            throw new ApiException(HttpStatus.GONE,"TRANSFER_EXPIRED","대표 이전 제안이 만료됐어요.");
        if(state!=TransferState.PENDING||input.version()==null||input.version()!=transfer.version)
            throw ApiException.conflict("이전 제안이 바뀌었어요. 최신 내용을 확인해 주세요.");
        if(accept){
            if(!Boolean.TRUE.equals(input.adultConfirmed()))
                throw ApiException.invalid("성인 가족임을 확인해 주세요.");
            var from=activeInHouse(h,transfer.fromMemberId);
            if(!house.ownerId.equals(from.userId)||!from.role.equals("OWNER")||!actor.role.equals("MEMBER"))
                throw ApiException.conflict("집 대표나 대상 가족이 바뀌었어요.");
            from.role="MEMBER";
            from.membershipVersion++;
            actor.role="OWNER";
            actor.membershipVersion++;
            house.ownerId=actor.userId;
        }
        return finish(house,transfer,result,now);
    }

    @EventListener
    void membershipEnding(MembershipEnding event){
        pending(event.household().id).filter(transfer->participates(transfer,event.memberId()))
            .ifPresent(transfer->finish(event.household(),transfer,TransferState.CANCELLED,now()));
    }

    private OwnershipView finish(Household house,OwnershipTransfer transfer,TransferState state,Instant now){
        transfer.transferState=state;
        transfer.respondedAt=now;
        transfers.flush();
        homes.emit(house,"OWNERSHIP_TRANSFER_CHANGED",Map.of());
        return view(transfer,now);
    }

    private Optional<OwnershipTransfer> pending(String house){
        return transfers.findFirstByHouseholdIdAndTransferStateOrderByCreatedAtDesc(house,TransferState.PENDING);
    }

    private Member activeInHouse(String house,String id){
        return members.findById(id).filter(member->member.householdId.equals(house)&&member.active)
            .orElseThrow(ApiException::forbidden);
    }

    private static boolean participates(OwnershipTransfer transfer,String id){
        return id.equals(transfer.fromMemberId)||id.equals(transfer.targetMemberId);
    }

    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static OwnershipView view(OwnershipTransfer transfer,Instant now){
        return new OwnershipView(transfer.id,transfer.fromMemberId,transfer.targetMemberId,
            transfer.stateAt(now),transfer.expiresAt,transfer.version);
    }
}

@RestController @RequestMapping("/api/households/{h}/ownership-transfers")
class OwnershipTransferController {
    private final OwnershipService service;
    OwnershipTransferController(OwnershipService service){this.service=service;}

    @GetMapping List<OwnershipView> current(@PathVariable String h,Principal user){
        return service.current(h,user.getName());
    }

    @PostMapping OwnershipView propose(@PathVariable String h,Principal user,@Valid @RequestBody ProposeOwnership input){
        return service.propose(h,user.getName(),input);
    }

    @PostMapping("/{id}/accept") OwnershipView accept(@PathVariable String h,@PathVariable String id,Principal user,
        @Valid @RequestBody DecideOwnership input){
        return service.decide(h,user.getName(),id,input,true);
    }

    @PostMapping("/{id}/cancel") OwnershipView cancel(@PathVariable String h,@PathVariable String id,Principal user,
        @Valid @RequestBody DecideOwnership input){
        return service.decide(h,user.getName(),id,input,false);
    }
}
