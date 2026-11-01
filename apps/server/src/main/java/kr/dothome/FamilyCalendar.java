package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
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
 boolean allDay;
 LocalDate startDate,endDate;
 Instant startAt,endAt;
 @Column(nullable=false,length=64) String timeZone;
 @Column(nullable=false,length=36) String createdByMemberId,updatedByMemberId;
 Instant createdAt=Instant.now(),updatedAt=Instant.now();
 @Version long version;
}
interface CalendarEvents extends JpaRepository<HouseholdCalendarEvent,String> {
 @org.springframework.data.jpa.repository.Query("select e from HouseholdCalendarEvent e where e.householdId=:h and ((e.allDay=true and e.startDate<=:toDate and e.endDate>=:fromDate) or (e.allDay=false and e.startAt<:until and (e.endAt>:since or (e.startAt=e.endAt and e.startAt>=:since)))) order by e.allDay desc,e.startDate,e.startAt,e.createdAt")
 List<HouseholdCalendarEvent> range(@org.springframework.data.repository.query.Param("h") String h,@org.springframework.data.repository.query.Param("fromDate") LocalDate fromDate,@org.springframework.data.repository.query.Param("toDate") LocalDate toDate,@org.springframework.data.repository.query.Param("since") Instant since,@org.springframework.data.repository.query.Param("until") Instant until);
}
record CalendarInput(@NotBlank @Size(max=120) String title,@NotNull @Size(max=2000) String description,@NotNull @Pattern(regexp="GENERAL|BIRTHDAY|ANNIVERSARY") String category,boolean allDay,LocalDate startDate,LocalDate endDate,Instant startAt,Instant endAt,@NotBlank @Size(max=64) String timeZone,@Min(0) long version) {}
record CalendarView(String id,String title,String description,String category,boolean allDay,LocalDate startDate,LocalDate endDate,Instant startAt,Instant endAt,String timeZone,String createdByMemberId,String updatedByMemberId,Instant createdAt,Instant updatedAt,long version) {}
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class CalendarService {
 final HomeService homes;final CalendarEvents events;
 CalendarService(HomeService homes,CalendarEvents events){this.homes=homes;this.events=events;}
 static final ZoneId DISPLAY=ZoneId.of("Asia/Seoul");
 static void validate(CalendarInput i){
  if(i.title().strip().isEmpty())throw ApiException.invalid("일정 제목을 입력해주세요.");
  try{ZoneId.of(i.timeZone());}catch(Exception e){throw ApiException.invalid("올바른 시간대를 선택해주세요.");}
  if(i.allDay()){if(i.startDate()==null||i.endDate()==null||i.startAt()!=null||i.endAt()!=null||i.endDate().isBefore(i.startDate()))throw ApiException.invalid("종일 일정의 시작·종료 날짜를 확인해주세요.");}
  else if(i.startAt()==null||i.endAt()==null||i.startDate()!=null||i.endDate()!=null||i.endAt().isBefore(i.startAt()))throw ApiException.invalid("일정 종료는 시작보다 빠를 수 없어요.");
 }
 List<CalendarView> range(String h,String u,LocalDate from,LocalDate to){homes.requireMember(h,u);if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>366)throw ApiException.invalid("조회 기간은 367일 이내로 선택해주세요.");return events.range(h,from,to,from.atStartOfDay(DISPLAY).toInstant(),to.plusDays(1).atStartOfDay(DISPLAY).toInstant()).stream().map(CalendarService::view).toList();}
 CalendarView save(String h,String u,String id,CalendarInput input){validate(input);Household house=homes.lock(h,u);Member me=homes.requireMember(h,u);HouseholdCalendarEvent e=id==null?new HouseholdCalendarEvent():scoped(h,id);if(id!=null&&e.version!=input.version())throw ApiException.conflict("다른 가족이 일정을 수정했어요. 최신 내용을 다시 확인해주세요.");e.householdId=h;e.title=input.title().strip();e.description=input.description().strip();e.category=input.category();e.allDay=input.allDay();e.startDate=input.startDate();e.endDate=input.endDate();e.startAt=input.startAt();e.endAt=input.endAt();e.timeZone=input.timeZone();if(id==null)e.createdByMemberId=me.id;e.updatedByMemberId=me.id;e.updatedAt=Instant.now();events.saveAndFlush(e);homes.emit(house,"CALENDAR_EVENT_CHANGED",e.id);return view(e);}
 void delete(String h,String u,String id,long version){Household house=homes.lock(h,u);HouseholdCalendarEvent e=scoped(h,id);if(e.version!=version)throw ApiException.conflict("일정이 변경됐어요. 다시 확인해주세요.");events.delete(e);events.flush();homes.emit(house,"CALENDAR_EVENT_CHANGED",id);}
 HouseholdCalendarEvent scoped(String h,String id){var e=events.findById(id).orElseThrow(ApiException::missing);if(!e.householdId.equals(h))throw ApiException.forbidden();return e;}
 static CalendarView view(HouseholdCalendarEvent e){return new CalendarView(e.id,e.title,e.description,e.category,e.allDay,e.startDate,e.endDate,e.startAt,e.endAt,e.timeZone,e.createdByMemberId,e.updatedByMemberId,e.createdAt,e.updatedAt,e.version);}
}
@RestController @RequestMapping("/api/households/{h}/calendar-events")
class CalendarController {
 final CalendarService service;CalendarController(CalendarService service){this.service=service;}
 @GetMapping List<CalendarView> range(@PathVariable String h,@RequestParam LocalDate from,@RequestParam LocalDate to,Principal p){return service.range(h,p.getName(),from,to);}
 @PostMapping CalendarView create(@PathVariable String h,Principal p,@Valid @RequestBody CalendarInput input){return service.save(h,p.getName(),null,input);}
 @PatchMapping("/{id}") CalendarView update(@PathVariable String h,@PathVariable String id,Principal p,@Valid @RequestBody CalendarInput input){return service.save(h,p.getName(),id,input);}
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void delete(@PathVariable String h,@PathVariable String id,@RequestParam long version,Principal p){service.delete(h,p.getName(),id,version);}
}
