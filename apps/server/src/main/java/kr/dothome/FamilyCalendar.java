package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Entity @Table(name="household_calendar_event")
class HouseholdCalendarEvent extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=120) String title;
    @Column(nullable=false,length=2000) String description;
    @Column(nullable=false,length=20) String category;
    @Column(nullable=false,length=24) String eventScope="HOUSEHOLD";
    Instant hiddenAt;
    boolean allDay;
    LocalDate startDate,endDate;
    Instant startAt,endAt;
    @Column(nullable=false,length=64) String timeZone;
    @Column(nullable=false,length=36) String createdByMemberId,updatedByMemberId;
    Instant createdAt=Instant.now(),updatedAt=Instant.now();
    @Version long version;
}
interface CalendarEvents extends JpaRepository<HouseholdCalendarEvent,String> {
    @org.springframework.data.jpa.repository.Query("""
        select e from HouseholdCalendarEvent e where e.householdId=:h and
        (:moderation=false and (e.eventScope='HOUSEHOLD' or (
            e.hiddenAt is null and exists (
                select m.id from Member m where m.id=e.createdByMemberId and m.active=true
            )
        ) or e.createdByMemberId=:memberId)
        or :moderation=true and e.eventScope='PERSONAL_SHARED' and e.hiddenAt is not null and exists (
            select m.id from Member m where m.id=e.createdByMemberId and m.active=true
        ))
        and ((e.allDay=true and e.startDate<=:toDate and e.endDate>=:fromDate)
          or (e.allDay=false and e.startAt<:until and
             (e.endAt>:since or (e.startAt=e.endAt and e.startAt>=:since))))
        order by e.allDay desc,e.startDate,e.startAt,e.createdAt
        """)
    List<HouseholdCalendarEvent> range(String h,String memberId,boolean moderation,LocalDate fromDate,LocalDate toDate,Instant since,Instant until);
}

record CalendarInput(@NotBlank @Size(max=120) String title,@NotNull @Size(max=2000) String description,
    @NotNull @Pattern(regexp="GENERAL|BIRTHDAY|ANNIVERSARY|CARE") String category,boolean allDay,
    LocalDate startDate,LocalDate endDate,Instant startAt,Instant endAt,
    @NotBlank @Size(max=64) String timeZone,@Min(0) long version,
    @Pattern(regexp="HOUSEHOLD|PERSONAL_SHARED") String scope) {
    CalendarInput(String title,String description,String category,boolean allDay,LocalDate startDate,LocalDate endDate,
        Instant startAt,Instant endAt,String timeZone,long version){
        this(title,description,category,allDay,startDate,endDate,startAt,endAt,timeZone,version,null);
    }
}
record HiddenCalendarEvent(String id,Instant hiddenAt,long version) {}
record CalendarView(String id,String title,String description,String category,boolean allDay,LocalDate startDate,
    LocalDate endDate,Instant startAt,Instant endAt,String timeZone,String createdByMemberId,String updatedByMemberId,
    Instant createdAt,Instant updatedAt,long version,String scope,Instant hiddenAt,boolean canEdit,boolean canHide) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class CalendarService {
    static final ZoneId DISPLAY=ZoneId.of("Asia/Seoul");
    final HomeService homes;
    final CalendarEvents events;
    final Members members;
    CalendarService(HomeService homes,CalendarEvents events,Members members){
        this.homes=homes;this.events=events;this.members=members;
    }
    static void validate(CalendarInput input){
        if(input.title().strip().isEmpty())throw ApiException.invalid("일정 제목을 입력해 주세요.");
        zone(input.timeZone());
        if(input.allDay()){
            if(input.startDate()==null||input.endDate()==null||input.startAt()!=null||input.endAt()!=null||
                input.endDate().isBefore(input.startDate()))
                throw ApiException.invalid("종일 일정의 시작·종료 날짜를 확인해 주세요.");
        }else if(input.startAt()==null||input.startDate()!=null||input.endDate()!=null||
            input.endAt()!=null&&input.endAt().isBefore(input.startAt())){
            throw ApiException.invalid("일정 종료는 시작보다 빠를 수 없어요.");
        }
    }
    private static ZoneId zone(String timeZone){
        if(!ZoneId.getAvailableZoneIds().contains(timeZone))throw ApiException.invalid("올바른 시간대를 선택해 주세요.");
        return ZoneId.of(timeZone);
    }
    List<CalendarView> range(String h,String u,LocalDate from,LocalDate to){
        return range(h,u,from,to,null);
    }
    List<CalendarView> range(String h,String u,LocalDate from,LocalDate to,String timeZone){
        Member me=homes.requireMember(h,u);
        return rows(h,me.id,false,from,to,timeZone).stream().map(event->view(event,me)).toList();
    }
    List<HiddenCalendarEvent> hidden(String h,String u,LocalDate from,LocalDate to){
        Member me=homes.requireMember(h,u);
        if(!"OWNER".equals(me.role))throw ApiException.forbidden();
        return rows(h,me.id,true,from,to,null).stream()
            .map(event->new HiddenCalendarEvent(event.id,event.hiddenAt,event.version)).toList();
    }
    private List<HouseholdCalendarEvent> rows(String h,String memberId,boolean moderation,LocalDate from,LocalDate to,String timeZone){
        if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>366)
            throw ApiException.invalid("조회 기간은 367일 이내로 선택해 주세요.");
        ZoneId zone=timeZone==null?DISPLAY:zone(timeZone);
        try{
            return events.range(h,memberId,moderation,from,to,from.atStartOfDay(zone).toInstant(),to.plusDays(1).atStartOfDay(zone).toInstant());
        }catch(DateTimeException e){throw ApiException.invalid("조회 날짜를 확인해 주세요.");}
    }
    CalendarView save(String h,String u,String id,CalendarInput input){
        Household house=homes.lock(h,u);
        Member me=homes.requireMember(h,u);
        validate(input);
        HouseholdCalendarEvent event=id==null?new HouseholdCalendarEvent():scoped(h,id);
        if(id!=null){
            if(!canEdit(event,me))throw ApiException.forbidden();
            checkVersion(event,input.version());
            if(input.scope()!=null&&!event.eventScope.equals(input.scope()))
                throw ApiException.invalid("이미 등록한 일정의 공유 종류는 바꿀 수 없어요.");
        }else{
            event.createdByMemberId=me.id;
            if(input.scope()!=null)event.eventScope=input.scope();
        }
        event.householdId=h;
        event.title=input.title().strip();
        event.description=input.description().strip();
        event.category=input.category();
        event.allDay=input.allDay();
        event.startDate=input.startDate();
        event.endDate=input.endDate();
        event.startAt=input.startAt();
        event.endAt=input.allDay()?null:Objects.requireNonNullElse(input.endAt(),input.startAt());
        event.timeZone=input.timeZone();
        event.updatedByMemberId=me.id;
        event.updatedAt=Instant.now();
        events.saveAndFlush(event);
        homes.emit(house,"CALENDAR_EVENT_CHANGED",event.id);
        return view(event,me);
    }
    void delete(String h,String u,String id,long version){
        Household house=homes.lock(h,u);
        Member me=homes.requireMember(h,u);
        HouseholdCalendarEvent event=scoped(h,id);
        if(!canEdit(event,me))throw ApiException.forbidden();
        checkVersion(event,version);
        events.delete(event);
        events.flush();
        homes.emit(house,"CALENDAR_EVENT_CHANGED",id);
    }
    void hide(String h,String u,String id,long version,boolean hidden){
        Household house=homes.lock(h,u);
        Member me=homes.requireMember(h,u);
        HouseholdCalendarEvent event=scoped(h,id);
        if(!canHide(event,me)||members.findById(event.createdByMemberId).filter(creator->creator.active).isEmpty())
            throw ApiException.forbidden();
        checkVersion(event,version);
        if(hidden==(event.hiddenAt!=null))return;
        event.hiddenAt=hidden?Instant.now():null;
        event.updatedByMemberId=me.id;
        event.updatedAt=Instant.now();
        events.flush();
        homes.emit(house,"CALENDAR_EVENT_CHANGED",id);
    }
    HouseholdCalendarEvent scoped(String h,String id){
        return events.findById(id).filter(e->e.householdId.equals(h)).orElseThrow(ApiException::forbidden);
    }
    private static void checkVersion(HouseholdCalendarEvent event,long version){
        if(event.version!=version)throw ApiException.conflict("일정이 변경됐어요. 최신 내용을 다시 확인해 주세요.");
    }
    private static boolean canEdit(HouseholdCalendarEvent event,Member me){
        return event.createdByMemberId.equals(me.id)||"HOUSEHOLD".equals(event.eventScope)&&"OWNER".equals(me.role);
    }
    private static boolean canHide(HouseholdCalendarEvent event,Member me){
        return "PERSONAL_SHARED".equals(event.eventScope)&&"OWNER".equals(me.role);
    }
    static CalendarView view(HouseholdCalendarEvent event,Member me){
        return new CalendarView(event.id,event.title,event.description,event.category,event.allDay,event.startDate,event.endDate,
            event.startAt,event.endAt,event.timeZone,event.createdByMemberId,event.updatedByMemberId,event.createdAt,event.updatedAt,
            event.version,event.eventScope,event.hiddenAt,canEdit(event,me),canHide(event,me));
    }
}

record CalendarVersion(@NotNull @Min(0) Long version) {}
@RestController @RequestMapping("/api/households/{h}/calendar-events")
class CalendarController {
    final CalendarService service;
    CalendarController(CalendarService service){this.service=service;}
    @GetMapping List<CalendarView> range(@PathVariable String h,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) String timeZone,Principal p){
        return service.range(h,p.getName(),from,to,timeZone);
    }
    @GetMapping("/moderation") List<HiddenCalendarEvent> hidden(@PathVariable String h,@RequestParam LocalDate from,
        @RequestParam LocalDate to,Principal p){
        return service.hidden(h,p.getName(),from,to);
    }
    @PostMapping CalendarView create(@PathVariable String h,Principal p,@Valid @RequestBody CalendarInput input){
        return service.save(h,p.getName(),null,input);
    }
    @PatchMapping("/{id}") CalendarView update(@PathVariable String h,@PathVariable String id,Principal p,@Valid @RequestBody CalendarInput input){
        return service.save(h,p.getName(),id,input);
    }
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void delete(@PathVariable String h,@PathVariable String id,@RequestParam long version,Principal p){
        service.delete(h,p.getName(),id,version);
    }
    @PostMapping("/{id}/hide") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void hide(@PathVariable String h,@PathVariable String id,Principal p,@Valid @RequestBody CalendarVersion input){
        service.hide(h,p.getName(),id,input.version(),true);
    }
    @PostMapping("/{id}/unhide") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void unhide(@PathVariable String h,@PathVariable String id,Principal p,@Valid @RequestBody CalendarVersion input){
        service.hide(h,p.getName(),id,input.version(),false);
    }
}
