package kr.dothome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

record LeaveHouse(@NotBlank @Size(max=80) String requestId) {}
record MembershipEnding(Household household,String memberId) {}
record CommittedMembershipExit(String householdId,String userId) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class MembershipLifecycle {
    private final HomeService homes;
    private final Households households;
    private final Members members;
    private final Pets pets;
    private final DependentProfiles dependents;
    private final HouseholdRequestService requests;
    private final ApplicationEventPublisher publisher;

    MembershipLifecycle(HomeService homes,Households households,Members members,Pets pets,
        DependentProfiles dependents,HouseholdRequestService requests,ApplicationEventPublisher publisher){
        this.homes=homes;this.households=households;this.members=members;this.pets=pets;
        this.dependents=dependents;this.requests=requests;this.publisher=publisher;
    }

    void leave(String h,String user,LeaveHouse input){
        if(members.existsByHouseholdIdAndUserIdAndExitRequestId(h,user,input.requestId()))return;
        homes.requireMember(h,user);
        var house=households.locked(h).orElseThrow(ApiException::missing);
        if(members.existsByHouseholdIdAndUserIdAndExitRequestId(h,user,input.requestId()))return;
        var member=members.current(h,user).orElseThrow(ApiException::forbidden);
        end(house,member,member,input.requestId());
    }

    void remove(String h,String user,String memberId,long version){
        var house=homes.lock(h,user);
        homes.owner(house,user);
        var actor=homes.requireMember(h,user);
        var member=members.findById(memberId).filter(m->m.householdId.equals(h)&&m.active)
            .orElseThrow(ApiException::forbidden);
        if(member.membershipVersion!=version)
            throw ApiException.conflict("가족 구성원이 변경됐어요. 최신 목록을 확인해 주세요.");
        end(house,actor,member,null);
    }

    private void end(Household house,Member actor,Member member,String requestId){
        if(house.ownerId.equals(member.userId))
            throw new ApiException(HttpStatus.FORBIDDEN,"OWNER_EXIT_BLOCKED","집 대표는 소유권을 넘긴 뒤 나갈 수 있어요.");
        var owner=homes.requireMember(house.id,house.ownerId);
        var now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        member.active=false;
        member.endedAt=now;
        member.exitRequestId=requestId;
        member.membershipVersion++;
        for(var pet:pets.findByHouseholdIdAndGuardianMemberIdAndArchivedAtIsNull(house.id,member.id))
            pet.guardianMemberId=owner.id;
        for(var profile:dependents.findByHouseholdIdAndGuardianMemberIdAndArchivedAtIsNull(house.id,member.id))
            profile.guardianMemberId=owner.id;
        publisher.publishEvent(new CommittedMembershipExit(house.id,member.userId));
        publisher.publishEvent(new MembershipEnding(house,member.id));
        requests.releaseDeparted(house,actor,member.id,now);
        homes.emit(house,"MEMBER_DEPARTED",Map.of());
    }
}

@RestController @RequestMapping("/api/households/{h}")
class MembershipLifecycleController {
    private final MembershipLifecycle membership;
    MembershipLifecycleController(MembershipLifecycle membership){this.membership=membership;}

    @PostMapping("/me/leave") @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@PathVariable String h,Principal user,@Valid @RequestBody LeaveHouse input){
        membership.leave(h,user.getName(),input);
    }

    @DeleteMapping("/members/{memberId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable String h,@PathVariable String memberId,Principal user,@RequestParam @PositiveOrZero long version){
        membership.remove(h,user.getName(),memberId,version);
    }
}
