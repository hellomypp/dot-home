package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

enum CareProfileType { PET, BABY }
record CareProfileArchived(Household household,Member actor,CareProfileType type,String id) {}

@Entity @Table(name="repeat_care_rule")
class RepeatCareRule extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=8) CareProfileType profileType;
    @Column(nullable=false,length=36) String profileId;
    @Column(nullable=false,length=80) String title;
    @Column(nullable=false) int weekdayMask;
    @Column(nullable=false) LocalTime localTime;
    @Column(nullable=false,length=64) String timeZone;
    @Column(nullable=false) Instant nextAt;
    Instant endedAt;
    @Version long version;

    CareTime schedule(){return new CareTime(ZoneId.of(timeZone),localTime,weekdayMask);}
}

interface RepeatCareRules extends JpaRepository<RepeatCareRule,String> {
    List<RepeatCareRule> findByHouseholdIdAndEndedAtIsNullOrderByNextAtAscIdAsc(String house);
    List<RepeatCareRule> findByHouseholdIdAndProfileTypeAndProfileIdAndEndedAtIsNull(String house,CareProfileType type,String id);
    @Query("select r.id from RepeatCareRule r where r.endedAt is null and r.nextAt<=:now order by r.nextAt,r.id")
    List<String> due(Instant now,Pageable page);
}

record CareTime(ZoneId zone,LocalTime localTime,int weekdayMask) {
    static int bit(DayOfWeek day){return 1<<(day.getValue()-1);}

    Instant on(LocalDate day){
        if((weekdayMask&bit(day.getDayOfWeek()))==0)return null;
        var local=day.atTime(localTime);
        var rules=zone.getRules();
        var offsets=rules.getValidOffsets(local);
        return offsets.isEmpty()?rules.getTransition(local).getInstant():local.toInstant(offsets.getFirst());
    }

    Instant next(Instant after){
        var today=LocalDate.ofInstant(after,zone);
        for(int d=0;d<=7;d++){
            var candidate=on(today.plusDays(d));
            if(candidate!=null&&candidate.isAfter(after))return candidate;
        }
        throw new IllegalStateException("Care rule has no next occurrence");
    }

    Instant latest(Instant now){
        var today=LocalDate.ofInstant(now,zone);
        for(int d=0;d<=7;d++){
            var candidate=on(today.minusDays(d));
            if(candidate!=null&&!candidate.isAfter(now))return candidate;
        }
        throw new IllegalStateException("Care rule has no previous occurrence");
    }

    List<DayOfWeek> days(){
        return Arrays.stream(DayOfWeek.values()).filter(day->(weekdayMask&bit(day))!=0).toList();
    }
}

record RepeatCareInput(@NotNull CareProfileType profileType,@NotBlank @Size(max=36) String profileId,
    @NotBlank String title,@NotEmpty List<@NotNull DayOfWeek> weekdays,@NotNull LocalTime localTime,
    @NotBlank @Size(max=64) String timeZone,@PositiveOrZero Long version) {}
record RepeatCareView(String id,CareProfileType profileType,String profileId,String title,List<DayOfWeek> weekdays,
    LocalTime localTime,String timeZone,Instant nextAt,long version,boolean canEdit) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class RepeatCareService {
    private record CareTarget(String name,String guardian) {}

    private final HomeService homes;
    private final RepeatCareRules rules;
    private final HouseholdRequestService requests;
    private final Members members;
    private final Households households;
    private final Pets pets;
    private final DependentProfiles dependents;
    @PersistenceContext private EntityManager entities;

    RepeatCareService(HomeService homes,RepeatCareRules rules,HouseholdRequestService requests,Members members,
        Households households,Pets pets,DependentProfiles dependents){
        this.homes=homes;this.rules=rules;this.requests=requests;this.members=members;
        this.households=households;this.pets=pets;this.dependents=dependents;
    }

    List<RepeatCareView> list(String house,String user){
        var household=homes.lock(house,user);
        var actor=homes.requireMember(house,user);
        return rules.findByHouseholdIdAndEndedAtIsNullOrderByNextAtAscIdAsc(house).stream()
            .map(rule->view(rule,canEdit(household,actor,target(house,rule.profileType,rule.profileId).guardian()))).toList();
    }

    RepeatCareView save(String house,String user,String id,RepeatCareInput input){
        var household=homes.lock(house,user);
        var actor=homes.requireMember(house,user);
        String guardian=target(house,input.profileType(),input.profileId()).guardian();
        if(!canEdit(household,actor,guardian))throw ApiException.forbidden();
        var rule=id==null?new RepeatCareRule():scoped(house,id,input.version());
        if(id!=null&&(rule.profileType!=input.profileType()||!rule.profileId.equals(input.profileId())))
            throw ApiException.invalid("이미 만든 반복 돌봄의 대상은 바꿀 수 없어요.");
        var time=validTime(input);
        rule.householdId=house;
        rule.profileId=input.profileId();
        rule.profileType=input.profileType();
        rule.title=HouseholdRequestService.text(input.title(),1,80,"제목");
        boolean changed=rule.weekdayMask!=time.weekdayMask()||!time.localTime().equals(rule.localTime)||!time.zone().getId().equals(rule.timeZone);
        rule.weekdayMask=time.weekdayMask();
        rule.localTime=time.localTime();
        rule.timeZone=time.zone().getId();
        if(changed)rule.nextAt=time.next(Instant.now());
        rules.saveAndFlush(rule);
        homes.emit(household,"CARE_RULE_CHANGED",rule.id);
        return view(rule,true);
    }

    void end(String house,String user,String id,long version){
        var household=homes.lock(house,user);
        var actor=homes.requireMember(house,user);
        var rule=scoped(house,id,version);
        if(!canEdit(household,actor,target(house,rule.profileType,rule.profileId).guardian()))throw ApiException.forbidden();
        endRule(household,actor,rule,Instant.now());
    }

    @EventListener
    void profileArchived(CareProfileArchived event){
        var now=Instant.now();
        for(var rule:rules.findByHouseholdIdAndProfileTypeAndProfileIdAndEndedAtIsNull(event.household().id,event.type(),event.id())){
            endRule(event.household(),event.actor(),rule,now);
        }
    }

    void generate(String id,Instant now){
        var candidate=rules.findById(id).orElse(null);
        if(candidate==null)return;
        var house=households.locked(candidate.householdId).orElse(null);
        if(house==null)return;
        // The initial ID lookup may precede another scheduler transaction.
        entities.refresh(candidate);
        if(candidate.endedAt!=null||candidate.nextAt.isAfter(now))return;
        var schedule=candidate.schedule();
        Instant occurrence=schedule.latest(now);
        if(!occurrence.isBefore(candidate.nextAt)&&occurrence.plus(Duration.ofHours(24)).isAfter(now)){
            var target=target(house.id,candidate.profileType,candidate.profileId);
            var actor=members.findById(Objects.toString(target.guardian(),"")).filter(m->m.active)
                .or(()->members.findByHouseholdIdAndUserIdAndActiveTrue(house.id,house.ownerId)).orElse(null);
            if(actor!=null)requests.generateCare(house,actor,candidate,target.name(),occurrence,now);
        }
        candidate.nextAt=schedule.next(now);
    }

    private RepeatCareRule scoped(String house,String id,Long version){
        var rule=rules.findById(id).orElseThrow(ApiException::missing);
        if(!rule.householdId.equals(house))throw ApiException.forbidden();
        if(rule.endedAt!=null)throw ApiException.missing();
        if(version==null||version!=rule.version)
            throw ApiException.conflict("반복 돌봄이 변경됐어요. 최신 설정을 확인해 주세요.");
        return rule;
    }

    private CareTarget target(String house,CareProfileType type,String id){
        if(type==CareProfileType.PET){
            var pet=pets.findById(id).filter(p->p.householdId.equals(house)&&p.archivedAt==null)
                .orElseThrow(ApiException::forbidden);
            return new CareTarget(pet.name,pet.guardianMemberId);
        }
        var baby=dependents.findById(id).filter(p->p.householdId.equals(house)&&p.archivedAt==null)
            .orElseThrow(ApiException::forbidden);
        return new CareTarget(baby.name,baby.guardianMemberId);
    }

    private void endRule(Household house,Member actor,RepeatCareRule rule,Instant now){
        rule.endedAt=now;
        requests.cancelCare(house,actor,rule.id,now);
        homes.emit(house,"CARE_RULE_CHANGED",rule.id);
    }

    private static boolean canEdit(Household house,Member actor,String guardian){
        return house.ownerId.equals(actor.userId)||actor.id.equals(guardian);
    }

    private static CareTime validTime(RepeatCareInput input){
        var time=input.localTime();
        if(time==null||time.getSecond()!=0||time.getNano()!=0||input.weekdays()==null||input.weekdays().isEmpty()
            ||input.weekdays().stream().anyMatch(Objects::isNull)||!ZoneId.getAvailableZoneIds().contains(input.timeZone()))
            throw ApiException.invalid("반복할 요일, 시각과 시간대를 확인해 주세요.");
        int mask=0;
        for(var day:input.weekdays())mask|=CareTime.bit(day);
        return new CareTime(ZoneId.of(input.timeZone()),time,mask);
    }

    private static RepeatCareView view(RepeatCareRule rule,boolean canEdit){
        return new RepeatCareView(rule.id,rule.profileType,rule.profileId,rule.title,rule.schedule().days(),
            rule.localTime,rule.timeZone,rule.nextAt,rule.version,canEdit);
    }
}

@Component
class RepeatCareScheduler {
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger(RepeatCareScheduler.class);
    private final RepeatCareRules rules;
    private final RepeatCareService service;
    RepeatCareScheduler(RepeatCareRules rules,RepeatCareService service){this.rules=rules;this.service=service;}

    @Scheduled(fixedDelay=30_000)
    void tick(){
        var now=Instant.now();
        for(String id:rules.due(now,Pageable.ofSize(100))){
            try{service.generate(id,now);}
            catch(RuntimeException e){LOG.warn("Repeat care generation failed for rule {}",id,e);}
        }
    }
}

@RestController @RequestMapping("/api/households/{house}/care-rules")
class RepeatCareController {
    private final RepeatCareService service;
    RepeatCareController(RepeatCareService service){this.service=service;}

    @GetMapping List<RepeatCareView> list(@PathVariable String house,Principal user){
        return service.list(house,user.getName());
    }

    @PostMapping RepeatCareView add(@PathVariable String house,Principal user,@Valid @RequestBody RepeatCareInput input){
        return service.save(house,user.getName(),null,input);
    }

    @PatchMapping("/{id}") RepeatCareView edit(@PathVariable String house,@PathVariable String id,Principal user,
        @Valid @RequestBody RepeatCareInput input){
        return service.save(house,user.getName(),id,input);
    }

    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void end(@PathVariable String house,@PathVariable String id,Principal user,@RequestParam @PositiveOrZero long version){
        service.end(house,user.getName(),id,version);
    }
}
