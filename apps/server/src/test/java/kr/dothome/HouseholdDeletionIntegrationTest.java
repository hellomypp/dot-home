package kr.dothome;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="app.household-deletion-enabled=true") @ActiveProfiles({"dev","test"})
class HouseholdDeletionIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired HouseholdDeletionService deletion;
    @Autowired HouseholdExportService exports;
    @Autowired ObjectNoteService notes;
    @Autowired CalendarService calendar;
    @Autowired HousewarmingService visits;
    @Autowired HouseholdRequestService requests;
    @Autowired RepeatCareService care;
    @Autowired DependentService dependents;
    @Autowired OwnershipService ownership;
    @Autowired SpaceEditing editing;
    @Autowired JdbcTemplate sql;

    @Autowired javax.sql.DataSource source;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"검증 계정").id;}

    @Test void graceAllowsReadAndExportAndCancelNeverRestoresVisitorAccess(){
        var owner=user();
        var family=user();
        var stranger=user();
        var house=homes.create(owner,new Api.CreateHouse("가족 집","대표","sage")).household().id();
        homes.accept(homes.invite(house,owner).token(),family,new Api.Accept("가족","sage"));
        var familyLink=homes.invite(house,owner);
        String visitorLink=visits.create(house,owner).url().substring(7);
        visits.enter(visitorLink,stranger);

        var input=new RequestHouseDeletion("once",0L);
        assertEquals(403,assertThrows(ApiException.class,()->deletion.request(house,family,input)).status.value());
        var pending=deletion.request(house,owner,input);
        assertEquals(pending,deletion.request(house,owner,input));
        assertTrue(pending.pendingDeleteUntil().isAfter(Instant.now().plus(Duration.ofDays(6))));
        assertEquals(pending.pendingDeleteUntil(),homes.world(house,family).household().pendingDeleteUntil());
        assertEquals(409,assertThrows(ApiException.class,()->notes.create(house,family,"fridge",new NoteInput("차단"))).status.value());
        assertEquals(410,assertThrows(ApiException.class,()->homes.preview(familyLink.token())).status.value());
        assertEquals(410,assertThrows(ApiException.class,()->homes.accept(familyLink.token(),stranger,new Api.Accept("다른 계정","sage"))).status.value());
        assertEquals(410,assertThrows(ApiException.class,()->visits.world(visitorLink,stranger)).status.value());
        assertNotNull(exports.start(house,family,new ExportInput("export")).id());
        assertEquals(403,assertThrows(ApiException.class,()->deletion.cancel(house,family,new CancelHouseDeletion(pending.version()))).status.value());
        assertEquals(409,assertThrows(ApiException.class,()->deletion.cancel(house,owner,new CancelHouseDeletion(0L))).status.value());
        assertNull(deletion.cancel(house,owner,new CancelHouseDeletion(pending.version())).pendingDeleteUntil());
        assertNull(deletion.request(house,owner,input).pendingDeleteUntil());
        assertEquals(410,assertThrows(ApiException.class,()->visits.world(visitorLink,stranger)).status.value());
        assertNotNull(notes.create(house,family,"fridge",new NoteInput("쓰기 복구")));
        assertNotNull(homes.preview(familyLink.token()));
    }

    @Test void duePurgeRemovesRelatedRowsButKeepsAccountsOtherHomesAndTombstone(){
        var owner=user();
        var family=user();
        var friend=user();
        var house=homes.create(owner,new Api.CreateHouse("삭제 대상 집","대표","sage")).household().id();
        var target=homes.accept(homes.invite(house,owner).token(),family,new Api.Accept("가족","sage")).meMemberId();
        var other=homes.create(family,new Api.CreateHouse("다른 집","가족","sage")).household().id();
        var pet=homes.addPet(house,owner,new Api.PetInput("모카","cat","cream")).pets().getFirst();
        dependents.save(house,family,null,new DependentInput("BABY","별이",null,null,null));
        ownership.propose(house,owner,new ProposeOwnership("proposal",target));
        care.save(house,owner,null,new RepeatCareInput(CareProfileType.PET,pet.id(),"돌봄",List.of(DayOfWeek.MONDAY),LocalTime.NOON,"Asia/Seoul",null));
        var note=notes.create(house,owner,"fridge",new NoteInput("공동 쪽지"));
        notes.take(house,family,note.id(),note.version());
        var request=requests.create(house,owner,new CreateRequest("notice",RequestKind.NOTICE,"공지","",List.of(),null));
        requests.acknowledge(house,family,request.id());
        LocalDate date=LocalDate.of(2026,11,3);
        var event=calendar.save(house,owner,null,new CalendarInput("가족 일정","","GENERAL",true,date,date,null,null,"Asia/Seoul",0));
        calendar.respond(house,family,event.id(),new AttendanceInput(AttendanceStatus.ACCEPTED,0L));
        String token=visits.create(house,owner).url().substring(7);
        visits.enter(token,friend);
        visits.write(token,friend,new GuestbookInput("visit","반가워요"));
        homes.status(house,family,new Api.Status(null,null,null,"현재 상태",60));
        homes.event(house,family,new Api.CreateEvent("event","NOTE","공유 기록"));
        var export=exports.start(house,family,new ExportInput("export"));
        exports.generate(export.id());
        homes.archivePet(house,owner,pet.id(),pet.version());
        var session=editing.start(house,owner,new SpaceEditStart("edit",null));
        var pending=deletion.request(house,owner,new RequestHouseDeletion("delete",0L));
        assertEquals("ENDED",editing.current(house,owner).session().status());
        assertEquals(409,assertThrows(ApiException.class,()->editing.commit(house,owner,session.id(),
            new SpaceEditCommit("blocked",0L,0L))).status.value());
        deletion.purge(house,pending.pendingDeleteUntil().minusSeconds(1));
        assertEquals(house,homes.world(house,owner).household().id());

        deletion.purge(house,pending.pendingDeleteUntil());
        deletion.purge(house,pending.pendingDeleteUntil());
        assertThrows(ApiException.class,()->homes.world(house,owner));
        assertEquals(other,homes.world(other,family).household().id());
        for(String table:List.of("membership","pet","dependent_profile","household_export","ownership_transfer","space_edit_session","household_notification",
            "repeat_care_rule","care_photo","household_request","object_note","household_calendar_event","guestbook_entry","life_event"))
            assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE household_id=?",Integer.class,house),table);
        assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM household_deletion_log WHERE household_id=?",Integer.class,house));
        assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=?",Integer.class,owner));
        assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=?",Integer.class,friend));
    }
    @Test void deletionReplayRemovesOnlyRestoredHomesWithATombstone(){
        var owner=user();
        var old=homes.create(owner,new Api.CreateHouse("복원된 삭제 집","대표","sage")).household().id();
        var active=homes.create(owner,new Api.CreateHouse("유지할 집","대표","sage")).household().id();
        var note=notes.create(old,owner,"fridge",new NoteInput("되살리면 안 되는 쪽지"));
        sql.update("INSERT INTO household_deletion_log(household_id,deleted_at) VALUES(?,CURRENT_TIMESTAMP)",old);
        assertEquals(1,HouseholdDeletionReplay.replay(source,false));
        assertEquals(note.id(),notes.list(old,owner,"fridge").getFirst().id());
        assertEquals(1,HouseholdDeletionReplay.replay(source,true));
        assertEquals(0,HouseholdDeletionReplay.replay(source,true));
        assertThrows(ApiException.class,()->homes.world(old,owner));
        assertEquals(active,homes.world(active,owner).household().id());
        assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM household_deletion_log WHERE household_id=?",Integer.class,old));
    }
}
