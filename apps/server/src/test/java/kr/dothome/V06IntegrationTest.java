package kr.dothome;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles({"dev","test"})
class V06IntegrationTest {
 @Autowired HomeService homes;@Autowired IdentityService identity;@Autowired ObjectNoteService notes;@Autowired ObjectNotes noteRows;@Autowired CalendarService calendar;@Autowired DependentService dependents;@Autowired HousewarmingService visits;@Autowired HousewarmingInvitations links;@Autowired Members members;@Autowired tools.jackson.databind.ObjectMapper json;
 String user(){return identity.resolve("test",UUID.randomUUID().toString(),"테스트 가족").id;}
 String house(String u){return homes.create(u,new Api.CreateHouse("공유 집","가족","sage")).household().id();}
 String join(String h,String owner){String u=user();homes.accept(homes.invite(h,owner).token(),u,new Api.Accept("다른 가족","blue"));return u;}
 String token(HousewarmingLink l){return l.url().substring("/visit/".length());}
 @Test void notesAreSharedArchivedAndScoped(){String a=user(),h=house(a),b=join(h,a),outsider=user();var n=notes.create(h,a,"fridge",new NoteInput("<script>alert(1)</script>"));assertEquals(homes.requireMember(h,a).id,n.authorMemberId());assertEquals(1,notes.list(h,b,"fridge").size());assertThrows(ApiException.class,()->notes.list(h,outsider,"fridge"));assertThrows(ApiException.class,()->notes.create(h,a,"unknown",new NoteInput("no")));assertThrows(ApiException.class,()->notes.delete(h,b,n.id(),n.version()));var taken=notes.take(h,b,n.id(),n.version());assertEquals("TAKEN",taken.state());assertEquals(homes.requireMember(h,b).id,taken.takenByMemberId());assertTrue(notes.list(h,a,"fridge").isEmpty());assertEquals(n.body(),notes.archive(h,a).getFirst().body());assertThrows(ApiException.class,()->notes.take(h,a,n.id(),n.version()));notes.delete(h,a,n.id(),taken.version());assertTrue(notes.archive(h,b).isEmpty());}
 @Test void concurrentNoteTakeSucceedsExactlyOnce()throws Exception {String a=user(),h=house(a),b=join(h,a);var note=notes.create(h,a,"fridge",new NoteInput("함께 읽어요"));try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var futures=List.of(a,b).stream().map(u->pool.submit(()->{start.await();try{notes.take(h,u,note.id(),note.version());return 1;}catch(ApiException e){assertEquals(409,e.status.value());return 0;}})).toList();start.countDown();assertEquals(1,futures.get(0).get(10,TimeUnit.SECONDS)+futures.get(1).get(10,TimeUnit.SECONDS));}assertEquals(1,notes.archive(h,a).size());}
 @Test void noteLimitCountsCodePointsAndPreservesLegacyBody(){
  String a=user(),h=house(a);
  String limit="🐾".repeat(300);
  assertEquals(limit,notes.create(h,a,"fridge",new NoteInput(" "+limit+" ")).body());
  var tooLong=assertThrows(ApiException.class,()->notes.create(h,a,"fridge",new NoteInput(limit+"🐾")));
  assertEquals(400,tooLong.status.value());

  var legacy=new ObjectNote();
  legacy.householdId=h;legacy.objectId="fridge";legacy.authorMemberId=homes.requireMember(h,a).id;legacy.body="한".repeat(1000);
  noteRows.saveAndFlush(legacy);
  assertEquals(legacy.body,notes.list(h,a,"fridge").stream().filter(n->n.id().equals(legacy.id)).findFirst().orElseThrow().body());
  notes.take(h,a,legacy.id,legacy.version);
  assertEquals(legacy.body,notes.archive(h,a).getFirst().body());
 }
 @Test void concurrentCreationKeepsAtMostTenAttachedNotes()throws Exception {
  String a=user(),h=house(a),b=join(h,a);
  for(int i=0;i<9;i++)notes.create(h,a,"fridge",new NoteInput("기존 "+i));
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);
   var futures=List.of(a,b).stream().map(u->pool.submit(()->{
    start.await();
    try{notes.create(h,u,"fridge",new NoteInput("새 쪽지"));return 1;}
    catch(ApiException e){assertEquals(409,e.status.value());return 0;}
   })).toList();
   start.countDown();
   assertEquals(1,futures.get(0).get(10,TimeUnit.SECONDS)+futures.get(1).get(10,TimeUnit.SECONDS));
  }
  var attached=notes.list(h,b,"fridge");
  assertEquals(10,attached.size());
  var first=attached.getFirst();
  notes.take(h,b,first.id(),first.version());
  notes.create(h,a,"fridge",new NoteInput("비운 자리에 새 쪽지"));
  assertEquals(10,notes.list(h,a,"fridge").size());
  assertEquals(1,notes.archive(h,a).size());
 }
 @Test void archivePagesUseStableOrderAcrossIdenticalTimestamps(){
  String owner=user(),h=house(owner),member=homes.requireMember(h,owner).id;
  Instant created=Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  for(int i=0;i<5;i++){
   var note=new ObjectNote();
   note.householdId=h;note.objectId="fridge";note.authorMemberId=member;note.body="보관 쪽지 "+i;
   note.createdAt=created;note.state="TAKEN";note.takenByMemberId=member;note.takenAt=created;
   noteRows.saveAndFlush(note);
  }
  var first=notes.archivePage(h,owner,null,2);
  var second=notes.archivePage(h,owner,first.nextCursor(),2);
  var last=notes.archivePage(h,owner,second.nextCursor(),2);
  assertNull(last.nextCursor());
  var ids=java.util.stream.Stream.of(first,second,last).flatMap(page->page.items().stream()).map(ObjectNoteView::id).toList();
  assertEquals(5,ids.size());
  assertEquals(notes.archive(h,owner).stream().map(ObjectNoteView::id).sorted(Comparator.reverseOrder()).toList(),ids);
  assertThrows(ApiException.class,()->notes.archivePage(h,owner,"invalid",2));
  assertThrows(ApiException.class,()->notes.archivePage(h,owner,null,101));
  assertThrows(ApiException.class,()->notes.archivePage(h,user(),first.nextCursor(),2));
 }
 CalendarInput allDay(long version){return new CalendarInput("생일","가족 일정","BIRTHDAY",true,LocalDate.of(2026,11,3),LocalDate.of(2026,11,3),null,null,"Asia/Seoul",version);}
 @Test void calendarDatesVersionsAndScope(){String a=user(),h=house(a),b=join(h,a);var e=calendar.save(h,a,null,allDay(0));assertEquals(LocalDate.of(2026,11,3),e.startDate());assertEquals(1,calendar.range(h,b,LocalDate.of(2026,11,1),LocalDate.of(2026,11,30)).size());assertEquals(403,assertThrows(ApiException.class,()->calendar.save(h,b,e.id(),allDay(e.version()))).status.value());var changed=calendar.save(h,a,e.id(),allDay(e.version()));assertEquals(homes.requireMember(h,a).id,changed.updatedByMemberId());assertThrows(ApiException.class,()->calendar.save(h,a,e.id(),allDay(e.version())));assertThrows(ApiException.class,()->calendar.range(h,user(),LocalDate.of(2026,11,1),LocalDate.of(2026,11,30)));assertThrows(ApiException.class,()->calendar.range(h,a,LocalDate.of(2026,1,1),LocalDate.of(2028,1,1)));calendar.delete(h,a,e.id(),changed.version());assertTrue(calendar.range(h,b,LocalDate.of(2026,11,1),LocalDate.of(2026,11,30)).isEmpty());}
 @Test void personalCalendarScopeRestrictsEditingAndModeratesVisibility(){
  String owner=user(),h=house(owner),creator=join(h,owner),other=join(h,owner);
  LocalDate date=LocalDate.of(2026,11,3);
  var personal=new CalendarInput("개인 공유","개인 메모","CARE",true,date,date,null,null,"Asia/Seoul",0,"PERSONAL_SHARED");
  var created=calendar.save(h,creator,null,personal);
  assertEquals("PERSONAL_SHARED",created.scope());
  assertTrue(created.canEdit());
  assertFalse(calendar.range(h,owner,date,date).getFirst().canEdit());
  assertEquals(403,assertThrows(ApiException.class,()->calendar.save(h,owner,created.id(),personal)).status.value());
  assertEquals(403,assertThrows(ApiException.class,()->calendar.hide(h,other,created.id(),created.version(),true)).status.value());
  calendar.hide(h,owner,created.id(),created.version(),true);
  assertTrue(calendar.range(h,owner,date,date).isEmpty());
  assertTrue(calendar.range(h,other,date,date).isEmpty());
  assertEquals(403,assertThrows(ApiException.class,()->calendar.hidden(h,other,date,date)).status.value());
  var metadata=calendar.hidden(h,owner,date,date).getFirst();
  assertEquals(created.id(),metadata.id());
  var hidden=calendar.range(h,creator,date,date).getFirst();
  assertNotNull(hidden.hiddenAt());
  calendar.hide(h,owner,created.id(),metadata.version(),false);
  assertEquals(created.id(),calendar.range(h,other,date,date).getFirst().id());

  var member=members.findById(created.createdByMemberId()).orElseThrow();
  member.active=false;members.saveAndFlush(member);
  assertTrue(calendar.range(h,owner,date,date).isEmpty());
 }
 @Test void optionalQueryTimezoneChangesTimedDayButNotAllDayDates(){
  String owner=user(),h=house(owner);
  LocalDate date=LocalDate.of(2026,11,3);
  Instant instant=Instant.parse("2026-11-03T02:00:00Z");
  var timed=calendar.save(h,owner,null,new CalendarInput("시각","", "GENERAL",false,null,null,instant,null,"America/New_York",0));
  assertEquals(timed.startAt(),timed.endAt());
  var allDay=calendar.save(h,owner,null,allDay(0));
  assertEquals(Set.of(timed.id(),allDay.id()),calendar.range(h,owner,date,date).stream().map(CalendarView::id).collect(java.util.stream.Collectors.toSet()));
  assertEquals(List.of(allDay.id()),calendar.range(h,owner,date,date,"America/New_York").stream().map(CalendarView::id).toList());
  assertEquals(List.of(timed.id()),calendar.range(h,owner,date.minusDays(1),date.minusDays(1),"America/New_York").stream().map(CalendarView::id).toList());
 }
 @Test void attendanceIsVersionedPerMemberAndOnlyActiveTargetsCanRespond(){
  String owner=user(),h=house(owner),guest=join(h,owner),other=join(h,owner);
  String target=homes.requireMember(h,guest).id;
  var pet=homes.addPet(h,owner,new Api.PetInput("모카","cat","cream")).pets().getFirst();
  LocalDate date=LocalDate.of(2026,11,3);
  var event=calendar.save(h,owner,null,new CalendarInput("돌봄","", "CARE",true,date,date,null,null,"Asia/Seoul",0,
      "HOUSEHOLD",List.of(target,pet.id()),json.readTree("30")));
  assertEquals(30,event.reminderMinutesBefore());
  assertFalse(event.canRespond());
  assertEquals(403,assertThrows(ApiException.class,()->calendar.respond(h,other,event.id(),new AttendanceInput(AttendanceStatus.ACCEPTED,0L))).status.value());
  var accepted=calendar.respond(h,guest,event.id(),new AttendanceInput(AttendanceStatus.ACCEPTED,0L));
  assertEquals(1,accepted.version());
  assertEquals(409,assertThrows(ApiException.class,()->calendar.respond(h,guest,event.id(),new AttendanceInput(AttendanceStatus.DECLINED,0L))).status.value());
  var family=calendar.range(h,guest,date,date).getFirst();
  assertEquals(accepted,family.myRsvp());
  assertEquals(Map.of(AttendanceStatus.ACCEPTED,1L),family.rsvpCounts());
  assertNull(calendar.range(h,owner,date,date).getFirst().myRsvp());
  var legacy=calendar.save(h,owner,event.id(),new CalendarInput("수정","", "CARE",true,date,date,null,null,"Asia/Seoul",event.version()));
  assertEquals(Set.of(target,pet.id()),Set.copyOf(legacy.targetIds()));
  assertEquals(30,legacy.reminderMinutesBefore());
  calendar.delete(h,owner,event.id(),legacy.version());
  assertTrue(calendar.range(h,guest,date,date).isEmpty());
 }
 @Test void dependentProfileIsNotAMemberAndArchivingKeepsCalendarHistory(){
  String owner=user(),h=house(owner),guardian=join(h,owner),other=join(h,owner),visitor=user();
  visits.enter(token(visits.create(h,owner)),visitor);
  String otherHouse=house(other),otherGuardian=homes.requireMember(otherHouse,other).id;
  assertThrows(ApiException.class,()->dependents.save(h,guardian,null,new DependentInput("BABY","별",otherGuardian,null,null)));
  var baby=dependents.save(h,guardian,null,new DependentInput("BABY"," 별 ",null,null,null));
  assertEquals("별",baby.name());assertEquals(homes.requireMember(h,guardian).id,baby.guardianMemberId());
  assertEquals(List.of(baby),homes.world(h,owner).dependents());
  assertEquals(3,homes.world(h,owner).members().size());
  assertThrows(ApiException.class,()->dependents.list(h,visitor));
  assertEquals(403,assertThrows(ApiException.class,()->dependents.archive(h,other,baby.id(),0)).status.value());
  var edited=dependents.save(h,guardian,baby.id(),new DependentInput(null,"별이",null,null,0L));
  assertEquals(1,edited.version());
  assertEquals(409,assertThrows(ApiException.class,()->dependents.archive(h,owner,baby.id(),0)).status.value());
  LocalDate date=LocalDate.of(2026,11,3);
  var care=new CalendarInput("아기 돌봄","","CARE",true,date,date,null,null,"Asia/Seoul",0,"HOUSEHOLD",List.of(baby.id()),null);
  var event=calendar.save(h,guardian,null,care);
  assertFalse(event.canRespond());
  dependents.archive(h,owner,baby.id(),edited.version());
  assertTrue(dependents.list(h,guardian).isEmpty());
  assertEquals(List.of(baby.id()),calendar.range(h,guardian,date,date).getFirst().targetIds());
  assertThrows(ApiException.class,()->calendar.save(h,guardian,null,care));
 }
 @Test void petArchiveRequiresGuardianAndPreservesExistingCareEvents(){
  String owner=user(),h=house(owner),guardian=join(h,owner),other=join(h,owner);
  var pet=homes.addPet(h,guardian,new Api.PetInput("별이","cat","cream")).pets().getFirst();
  LocalDate date=LocalDate.of(2026,11,3);
  var care=new CalendarInput("급여","","CARE",true,date,date,null,null,"Asia/Seoul",0,"HOUSEHOLD",List.of(pet.id()),null);
  calendar.save(h,guardian,null,care);
  assertEquals(403,assertThrows(ApiException.class,()->homes.archivePet(h,other,pet.id(),pet.version())).status.value());
  assertEquals(409,assertThrows(ApiException.class,()->homes.archivePet(h,owner,pet.id(),pet.version()+1)).status.value());
  assertTrue(homes.archivePet(h,guardian,pet.id(),pet.version()).pets().isEmpty());
  assertTrue(homes.world(h,owner).pets().isEmpty());
  assertEquals(List.of(pet.id()),calendar.range(h,owner,date,date).getFirst().targetIds());
  assertThrows(ApiException.class,()->calendar.save(h,owner,null,care));
  assertEquals(404,assertThrows(ApiException.class,()->homes.editPet(h,guardian,pet.id(),new Api.PetEdit("별","cat"))).status.value());
 }
 @Test void timedCalendarUsesUtcAndSeoulRange(){String a=user(),h=house(a);var i=new CalendarInput("자정 약속","","GENERAL",false,null,null,Instant.parse("2026-11-02T15:00:00Z"),Instant.parse("2026-11-02T16:00:00Z"),"Asia/Seoul",0);calendar.save(h,a,null,i);assertTrue(calendar.range(h,a,LocalDate.of(2026,11,2),LocalDate.of(2026,11,2)).isEmpty());assertEquals(1,calendar.range(h,a,LocalDate.of(2026,11,3),LocalDate.of(2026,11,3)).size());assertThrows(ApiException.class,()->CalendarService.validate(new CalendarInput("거꾸로","","GENERAL",false,null,null,i.endAt(),i.startAt(),"Asia/Seoul",0)));assertThrows(ApiException.class,()->CalendarService.validate(new CalendarInput("혼합","","GENERAL",true,LocalDate.now(),LocalDate.now(),Instant.now(),null,"Asia/Seoul",0)));}
 @Test void visitsNeverCreateFamilyMembershipAndRevocationRemovesAccess(){String a=user(),h=house(a),guest=user();var link=visits.create(h,a);String token=token(link);assertNotEquals(token,links.findById(link.id()).orElseThrow().tokenHash);assertThrows(ApiException.class,()->visits.world(token,guest));assertEquals("공유 집",visits.enter(token,guest).householdName());visits.enter(token,guest);assertTrue(members.findByUserIdAndActiveTrue(guest).isEmpty());assertEquals(1,homes.world(h,a).members().size());assertThrows(ApiException.class,()->homes.world(h,guest));assertThrows(ApiException.class,()->notes.list(h,guest,"fridge"));assertThrows(ApiException.class,()->calendar.range(h,guest,LocalDate.now(),LocalDate.now()));assertThrows(ApiException.class,()->visits.create(h,guest));visits.revoke(h,a,link.id());assertThrows(ApiException.class,()->visits.world(token,guest));assertThrows(ApiException.class,()->visits.enter(token,guest));}
 @Test void guestbookIsIdempotentLimitedAndModerated(){String a=user(),h=house(a),guest=user();var link=visits.create(h,a);String t=token(link);visits.enter(t,guest);var input=new GuestbookInput("request-1","<img src=x onerror=alert(1)>");var e=visits.write(t,guest,input);assertEquals(e.id(),visits.write(t,guest,input).id());assertThrows(ApiException.class,()->visits.write(t,guest,new GuestbookInput("request-2","반복")));assertEquals(1,visits.world(t,guest).guestbook().size());assertThrows(ApiException.class,()->visits.manage(h,guest));visits.hide(h,a,e.id());assertTrue(visits.world(t,guest).guestbook().isEmpty());assertTrue(visits.manage(h,a).getFirst().hidden());}
 @Test void expiredAndForeignVisitLinksAreDenied(){String a=user(),h=house(a),other=user(),h2=house(other);var link=visits.create(h,a);var row=links.findById(link.id()).orElseThrow();row.expiresAt=Instant.now().minusSeconds(1);links.saveAndFlush(row);assertThrows(ApiException.class,()->visits.enter(token(link),user()));assertThrows(ApiException.class,()->visits.revoke(h2,other,link.id()));}
 @Test void concurrentCalendarEditUsesOneVersion()throws Exception {String a=user(),h=house(a),b=join(h,a);var e=calendar.save(h,b,null,allDay(0));try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var futures=List.of(a,b).stream().map(u->pool.submit(()->{start.await();try{calendar.save(h,u,e.id(),allDay(e.version()));return 1;}catch(ApiException ex){assertEquals(409,ex.status.value());return 0;}})).toList();start.countDown();assertEquals(1,futures.get(0).get(10,TimeUnit.SECONDS)+futures.get(1).get(10,TimeUnit.SECONDS));}}
 @Test void foreignRecordIdsCannotCrossHouseholds(){String a=user(),h=house(a),b=user(),other=house(b);var n=notes.create(h,a,"fridge",new NoteInput("비공개"));assertThrows(ApiException.class,()->notes.take(other,b,n.id(),n.version()));var e=calendar.save(h,a,null,allDay(0));assertThrows(ApiException.class,()->calendar.delete(other,b,e.id(),e.version()));}
 @Test void concurrentGuestbookReplayCreatesOneEntry()throws Exception{String a=user(),h=house(a),guest=user();String t=token(visits.create(h,a));visits.enter(t,guest);try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var input=new GuestbookInput("same-request","방문 인사");var one=pool.submit(()->{start.await();return visits.write(t,guest,input).id();});var two=pool.submit(()->{start.await();return visits.write(t,guest,input).id();});start.countDown();assertEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));}assertEquals(1,visits.manage(h,a).size());}
 @Test void timedEventEndingAtMidnightDoesNotLeakIntoNextDay(){String a=user(),h=house(a);calendar.save(h,a,null,new CalendarInput("자정까지","","GENERAL",false,null,null,Instant.parse("2026-11-02T14:00:00Z"),Instant.parse("2026-11-02T15:00:00Z"),"Asia/Seoul",0));assertEquals(1,calendar.range(h,a,LocalDate.of(2026,11,2),LocalDate.of(2026,11,2)).size());assertTrue(calendar.range(h,a,LocalDate.of(2026,11,3),LocalDate.of(2026,11,3)).isEmpty());}
}
