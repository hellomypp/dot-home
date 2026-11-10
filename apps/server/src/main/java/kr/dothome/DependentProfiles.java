package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Entity @Table(name="dependent_profile")
class DependentProfile extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=16) String profileType="BABY";
    @Column(nullable=false,length=40) String name;
    @Column(nullable=false,length=36) String guardianMemberId;
    @Column(nullable=false,length=24) String appearanceProfile;
    Instant archivedAt;
    @Version long version;
}

interface DependentProfiles extends JpaRepository<DependentProfile,String> {
    List<DependentProfile> findByHouseholdIdAndArchivedAtIsNullOrderById(String householdId);
    List<DependentProfile> findByHouseholdIdAndGuardianMemberIdAndArchivedAtIsNull(String householdId,String guardian);
}

record DependentInput(@Pattern(regexp="BABY") String type,@NotBlank @Size(max=40) String name,
    @Size(max=36) String guardianMemberId,@Pattern(regexp="v2:child") String appearanceProfile,
    @PositiveOrZero Long version) {}

record DependentView(String id,String type,String name,String guardianMemberId,String appearanceProfile,long version) {
    static DependentView from(DependentProfile profile){
        return new DependentView(profile.id,profile.profileType,profile.name,profile.guardianMemberId,
            profile.appearanceProfile,profile.version);
    }
}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class DependentService {
    private final HomeService homes;
    private final DependentProfiles profiles;
    private final Members members;

    DependentService(HomeService homes,DependentProfiles profiles,Members members){
        this.homes=homes;this.profiles=profiles;this.members=members;
    }

    List<DependentView> list(String house,String user){
        homes.requireMember(house,user);
        return profiles.findByHouseholdIdAndArchivedAtIsNullOrderById(house).stream().map(DependentView::from).toList();
    }

    DependentView save(String house,String user,String id,DependentInput input){
        var household=homes.lock(house,user);
        var actor=homes.requireMember(house,user);
        if(input.type()!=null&&!"BABY".equals(input.type())
            ||input.appearanceProfile()!=null&&!"v2:child".equals(input.appearanceProfile()))
            throw ApiException.invalid("지원하는 돌봄 프로필을 선택해 주세요.");
        var profile=id==null?new DependentProfile():editable(household,actor,id,input.version());
        String name=input.name()==null?"":input.name().strip();
        if(name.isEmpty()||name.length()>40||name.codePoints().anyMatch(Character::isISOControl))
            throw ApiException.invalid("아기 이름은 줄바꿈 없이 1~40자로 입력해 주세요.");
        profile.householdId=house;
        profile.name=name;
        profile.appearanceProfile="v2:child";
        if(input.guardianMemberId()!=null){
            profile.guardianMemberId=members.findById(input.guardianMemberId())
                .filter(member->member.active&&member.householdId.equals(house))
                .orElseThrow(()->ApiException.invalid("이 집의 가족을 보호자로 선택해 주세요.")).id;
        }else if(id==null){
            profile.guardianMemberId=actor.id;
        }
        profiles.saveAndFlush(profile);
        homes.emit(household,"DEPENDENTS_CHANGED",Map.of("dependentId",profile.id));
        return DependentView.from(profile);
    }

    void archive(String house,String user,String id,long version){
        var household=homes.lock(house,user);
        var actor=homes.requireMember(house,user);
        var profile=editable(household,actor,id,version);
        profile.archivedAt=Instant.now();
        homes.profileArchived(household,actor,CareProfileType.BABY,id,profile.guardianMemberId,profile.name,"baby");
        homes.emit(household,"DEPENDENTS_CHANGED",Map.of("dependentId",profile.id));
    }

    private DependentProfile editable(Household household,Member actor,String id,Long version){
        var profile=profiles.findById(id).orElseThrow(ApiException::missing);
        if(!profile.householdId.equals(household.id))throw ApiException.forbidden();
        if(!household.ownerId.equals(actor.userId)&&!profile.guardianMemberId.equals(actor.id))
            throw ApiException.forbidden();
        if(profile.archivedAt!=null)throw ApiException.missing();
        if(version==null||version!=profile.version)
            throw ApiException.conflict("다른 기기에서 아기 프로필을 변경했어요. 최신 정보를 확인해 주세요.");
        return profile;
    }
}

@RestController @RequestMapping("/api/households/{house}/dependents")
class DependentController {
    private final DependentService service;
    DependentController(DependentService service){this.service=service;}

    @GetMapping List<DependentView> list(@PathVariable String house,Principal user){
        return service.list(house,user.getName());
    }

    @PostMapping DependentView add(@PathVariable String house,Principal user,@Valid @RequestBody DependentInput input){
        return service.save(house,user.getName(),null,input);
    }

    @PatchMapping("/{id}") DependentView edit(@PathVariable String house,@PathVariable String id,Principal user,
        @Valid @RequestBody DependentInput input){
        return service.save(house,user.getName(),id,input);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String house,@PathVariable String id,Principal user,@RequestParam @PositiveOrZero long version){
        service.archive(house,user.getName(),id,version);
    }
}
