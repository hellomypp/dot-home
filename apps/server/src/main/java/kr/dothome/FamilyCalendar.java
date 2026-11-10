package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    @Column(columnDefinition="TEXT") String targetIds;
    Integer reminderMinutesBefore;
    boolean allDay;
    LocalDate startDate,endDate;
    Instant startAt,endAt;
    @Column(nullable=false,length=64) String timeZone;
    @Column(nullable=false,length=36) String createdByMemberId,updatedByMemberId;
    @Column(length=36) String titleEditorMemberId,descriptionEditorMemberId;
    Instant createdAt=Instant.now(),updatedAt=Instant.now();
    @Version long version;
}
interface CalendarEvents extends JpaRepository<HouseholdCalendarEvent,String> {
    List<HouseholdCalendarEvent> findByHouseholdIdAndEventScopeOrderByCreatedAtAscIdAsc(String house,String scope,org.springframework.data.domain.Pageable page);
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

enum AttendanceStatus { ACCEPTED, DECLINED, TENTATIVE }
@Entity @Table(name="calendar_attendance")
class CalendarAttendance extends Row {
    @Column(nullable=false,length=36) String eventId;
    @Column(nullable=false,length=36) String memberId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) AttendanceStatus attendanceStatus;
    @Column(nullable=false) Instant updatedAt;
    long version;
}
interface CalendarAttendances extends JpaRepository<CalendarAttendance,String> {
    Optional<CalendarAttendance> findByEventIdAndMemberId(String eventId,String memberId);
    List<CalendarAttendance> findByEventIdIn(Collection<String> eventIds);
    List<CalendarAttendance> findByEventId(String eventId);
}
record AttendanceInput(@NotNull AttendanceStatus status,@NotNull @Min(0) Long version) {}
record AttendanceView(AttendanceStatus status,long version) {}

record CalendarInput(@NotBlank @Size(max=120) String title,@NotNull @Size(max=2000) String description,
    @NotNull @Pattern(regexp="GENERAL|BIRTHDAY|ANNIVERSARY|CARE") String category,boolean allDay,
    LocalDate startDate,LocalDate endDate,Instant startAt,Instant endAt,
    @NotBlank @Size(max=64) String timeZone,@Min(0) long version,
    @Pattern(regexp="HOUSEHOLD|PERSONAL_SHARED") String scope,
    List<@NotBlank @Size(max=36) String> targetIds,JsonNode reminderMinutesBefore) {
    CalendarInput(String title,String description,String category,boolean allDay,LocalDate startDate,LocalDate endDate,
        Instant startAt,Instant endAt,String timeZone,long version){
        this(title,description,category,allDay,startDate,endDate,startAt,endAt,timeZone,version,null,null,null);
    }
    CalendarInput(String title,String description,String category,boolean allDay,LocalDate startDate,LocalDate endDate,
        Instant startAt,Instant endAt,String timeZone,long version,String scope){
        this(title,description,category,allDay,startDate,endDate,startAt,endAt,timeZone,version,scope,null,null);
    }
}
record HiddenCalendarEvent(String id,Instant hiddenAt,long version) {}
record CalendarView(String id,String title,String description,String category,boolean allDay,LocalDate startDate,
    LocalDate endDate,Instant startAt,Instant endAt,String timeZone,String createdByMemberId,String updatedByMemberId,
    Instant createdAt,Instant updatedAt,long version,String scope,Instant hiddenAt,boolean canEdit,boolean canHide,List<String> targetIds,
    Integer reminderMinutesBefore,boolean canRespond,AttendanceView myRsvp,Map<AttendanceStatus,Long> rsvpCounts) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class CalendarService {
    static final ZoneId DISPLAY=ZoneId.of("Asia/Seoul");
    final HomeService homes;
    final CalendarEvents events;
    final Members members;
    final Pets pets;
    final DependentProfiles dependents;
    final CalendarAttendances attendance;
    final ObjectMapper json;
    CalendarService(HomeService homes,CalendarEvents events,Members members,Pets pets,DependentProfiles dependents,CalendarAttendances attendance,ObjectMapper json){
        this.homes=homes;this.events=events;this.members=members;this.pets=pets;this.dependents=dependents;this.attendance=attendance;this.json=json;
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
        var rows=rows(h,me.id,false,from,to,timeZone);
        if(rows.isEmpty())return List.of();
        var active=activeMembers(h);
        var byEvent=new HashMap<String,List<CalendarAttendance>>();
        for(var response:attendance.findByEventIdIn(rows.stream().map(e->e.id).toList())){
            if(active.contains(response.memberId))byEvent.computeIfAbsent(response.eventId,k->new ArrayList<>()).add(response);
        }
        return rows.stream().map(event->view(event,me,byEvent.getOrDefault(event.id,List.of()))).toList();
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
        if(input.targetIds()!=null){
            var targets=input.targetIds().stream().distinct().sorted().toList();
            var previous=targets(event);
            validateTargets(h,targets.stream().filter(target->!previous.contains(target)).toList());
            event.targetIds=json.writeValueAsString(targets);
            if(id!=null&&!targets.isEmpty()){
                attendance.deleteAll(attendance.findByEventId(id).stream()
                    .filter(response->!targets.contains(response.memberId)).toList());
            }
        }
        if(input.reminderMinutesBefore()!=null){
            var reminder=input.reminderMinutesBefore();
            if(reminder.isNull())event.reminderMinutesBefore=null;
            else{
                if(!reminder.isIntegralNumber()||!reminder.canConvertToInt()||!Set.of(0,10,30,60,1440).contains(reminder.asInt()))
                    throw ApiException.invalid("일정 알림 시간은 0·10·30·60·1440분 중 선택해 주세요.");
                event.reminderMinutesBefore=reminder.asInt();
            }
        }
        event.householdId=h;
        String title=input.title().strip();
        String description=input.description().strip();
        if(!title.equals(event.title))event.titleEditorMemberId=me.id;
        if(!description.equals(event.description))event.descriptionEditorMemberId=me.id;
        event.title=title;
        event.description=description;
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
        var active=activeMembers(h);
        return view(event,me,attendance.findByEventId(event.id).stream().filter(r->active.contains(r.memberId)).toList());
    }
    void delete(String h,String u,String id,long version){
        Household house=homes.lock(h,u);
        Member me=homes.requireMember(h,u);
        HouseholdCalendarEvent event=scoped(h,id);
        if(!canEdit(event,me))throw ApiException.forbidden();
        checkVersion(event,version);
        attendance.deleteAll(attendance.findByEventId(id));
        attendance.flush();
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
    AttendanceView respond(String h,String u,String id,AttendanceInput input){
        Household house=homes.lock(h,u);
        Member member=homes.requireMember(h,u);
        HouseholdCalendarEvent event=scoped(h,id);
        var targets=targets(event);
        if(event.hiddenAt!=null||!targets.isEmpty()&&!targets.contains(member.id)||
            "PERSONAL_SHARED".equals(event.eventScope)&&members.findById(event.createdByMemberId).filter(creator->creator.active).isEmpty())
            throw ApiException.forbidden();
        var response=attendance.findByEventIdAndMemberId(event.id,member.id).orElse(null);
        long current=response==null?0:response.version;
        if(current!=input.version())throw ApiException.conflict("참석 응답이 변경됐어요. 최신 내용을 확인해 주세요.");
        if(response!=null&&response.attendanceStatus==input.status())return new AttendanceView(response.attendanceStatus,response.version);
        if(response==null){
            response=new CalendarAttendance();
            response.eventId=id;response.memberId=member.id;
        }
        response.attendanceStatus=input.status();
        response.updatedAt=Instant.now();
        response.version=current+1;
        attendance.save(response);
        homes.emit(house,"CALENDAR_EVENT_CHANGED",id);
        return new AttendanceView(response.attendanceStatus,response.version);
    }
    private Set<String> activeMembers(String h){
        var active=new HashSet<String>();
        for(var member:members.findByHouseholdIdAndActiveTrueOrderById(h))active.add(member.id);
        return active;
    }
    private void validateTargets(String h,List<String> targets){
        if(targets.isEmpty())return;
        var allowed=activeMembers(h);
        for(var pet:pets.findByHouseholdIdAndArchivedAtIsNullOrderById(h))allowed.add(pet.id);
        for(var dependent:dependents.findByHouseholdIdAndArchivedAtIsNullOrderById(h))allowed.add(dependent.id);
        if(!allowed.containsAll(targets))throw ApiException.invalid("이 집의 가족과 돌봄 프로필만 일정 대상으로 선택할 수 있어요.");
    }
    private List<String> targets(HouseholdCalendarEvent event){
        return event.targetIds==null?List.of():json.readValue(event.targetIds,new TypeReference<List<String>>(){});
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
    private CalendarView view(HouseholdCalendarEvent event,Member me,List<CalendarAttendance> responses){
        var targets=targets(event);
        var counts=new EnumMap<AttendanceStatus,Long>(AttendanceStatus.class);
        AttendanceView mine=null;
        if(event.hiddenAt==null){
            for(var response:responses){
                if(!targets.isEmpty()&&!targets.contains(response.memberId))continue;
                counts.merge(response.attendanceStatus,1L,Long::sum);
                if(response.memberId.equals(me.id))mine=new AttendanceView(response.attendanceStatus,response.version);
            }
        }
        boolean canRespond=event.hiddenAt==null&&(targets.isEmpty()||targets.contains(me.id));
        return new CalendarView(event.id,event.title,event.description,event.category,event.allDay,event.startDate,event.endDate,
            event.startAt,event.endAt,event.timeZone,event.createdByMemberId,event.updatedByMemberId,event.createdAt,event.updatedAt,
            event.version,event.eventScope,event.hiddenAt,canEdit(event,me),canHide(event,me),targets,event.reminderMinutesBefore,
            canRespond,mine,counts);
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
    @PutMapping("/{id}/rsvp") AttendanceView respond(@PathVariable String h,@PathVariable String id,Principal p,
            @Valid @RequestBody AttendanceInput input){
        return service.respond(h,p.getName(),id,input);
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
