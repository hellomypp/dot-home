package kr.dothome;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class FormerNotesIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identities;
    @Autowired MembershipLifecycle membership;
    @Autowired ObjectNoteService objectNotes;
    @Autowired FormerRecordService formerNotes;
    @Autowired HouseholdExportService exports;
    @Autowired CalendarService calendar;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource dataSource;

    String user(){return identities.resolve("test",UUID.randomUUID().toString(),"이전 기록 검증").id;}
    String house(String owner,String family){
        var id=homes.create(owner,new Api.CreateHouse("이전 집","대표","sage")).household().id();
        homes.accept(homes.invite(id,owner).token(),family,new Api.Accept("가족","sage"));
        return id;
    }

    @Test void onlyTheFormerAuthorCanListOrRemoveAndSharedExportsExpire() {
        var owner=user();var family=user();var outsider=user();var old=house(owner,family);var current=house(owner,family);
        var note=objectNotes.create(old,family,"fridge",new NoteInput("내가 붙인 쪽지"));
        var second=objectNotes.create(old,family,"fridge",new NoteInput("두 번째 내 쪽지"));
        var other=objectNotes.create(old,owner,"fridge",new NoteInput("다른 사람의 내용"));
        var active=objectNotes.create(current,family,"fridge",new NoteInput("현재 집의 내 쪽지"));
        assertTrue(formerNotes.page(FormerRecordKind.OBJECT_NOTE,family,null,20).items().isEmpty());
        membership.leave(old,family,new LeaveHouse("leave"));
        assertThrows(ApiException.class,()->homes.world(old,family));
        var first=formerNotes.page(FormerRecordKind.OBJECT_NOTE,family,null,1);
        assertEquals(1,first.items().size());
        var page=formerNotes.page(FormerRecordKind.OBJECT_NOTE,family,first.nextCursor(),1);
        assertNull(page.nextCursor());
        assertEquals(java.util.Set.of(note.id(),second.id()),java.util.Set.of(first.items().getFirst().id(),page.items().getFirst().id()));
        assertTrue(formerNotes.page(FormerRecordKind.OBJECT_NOTE,outsider,first.nextCursor(),20).items().isEmpty());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(FormerRecordKind.OBJECT_NOTE,family,other.id(),other.version())).status.value());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(FormerRecordKind.OBJECT_NOTE,family,active.id(),active.version())).status.value());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(FormerRecordKind.OBJECT_NOTE,owner,note.id(),note.version())).status.value());
        assertEquals(409,assertThrows(ApiException.class,()->formerNotes.remove(FormerRecordKind.OBJECT_NOTE,family,note.id(),note.version()+1)).status.value());

        var export=exports.start(old,owner,new ExportInput("snapshot-before-removal"));
        exports.generate(export.id());
        var token=exports.status(old,owner,export.id()).downloadToken();
        assertTrue(exports.download(old,owner,export.id(),token).contains(note.body()));
        formerNotes.remove(FormerRecordKind.OBJECT_NOTE,family,note.id(),note.version());
        formerNotes.remove(FormerRecordKind.OBJECT_NOTE,family,note.id(),note.version());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(FormerRecordKind.OBJECT_NOTE,outsider,note.id(),note.version())).status.value());
        assertEquals(List.of(second.id()),formerNotes.page(FormerRecordKind.OBJECT_NOTE,family,null,20).items().stream().map(FormerRecord::id).toList());
        assertTrue(objectNotes.list(old,owner,"fridge").stream().noneMatch(row->row.id().equals(note.id())));
        assertTrue(objectNotes.list(old,owner,"fridge").stream().anyMatch(row->row.id().equals(other.id())));
        assertEquals(ExportState.CANCELLED,exports.status(old,owner,export.id()).state());
        assertEquals(410,assertThrows(ApiException.class,()->exports.download(old,owner,export.id(),token)).status.value());
    }

    @Test void backupReplayUsesBothOriginalHouseAndAuthorAndLeavesOtherNotes() {
        var owner=user();var family=user();var home=house(owner,family);var other=house(owner,family);
        var note=objectNotes.create(home,family,"fridge",new NoteInput("이전 복원에 있는 쪽지"));
        var keep=objectNotes.create(other,family,"fridge",new NoteInput("보존할 쪽지"));
        var export=exports.start(home,owner,new ExportInput("restored-file"));
        exports.generate(export.id());
        sql.update("""
            INSERT INTO former_record_deletion(record_kind,record_id,household_id,author_member_id,deleted_at)
            VALUES('OBJECT_NOTE',?,?,?,CURRENT_TIMESTAMP)
            """,note.id(),home,note.authorMemberId());
        sql.update("""
            INSERT INTO former_record_deletion(record_kind,record_id,household_id,author_member_id,deleted_at)
            VALUES('OBJECT_NOTE',?,?,?,CURRENT_TIMESTAMP)
            """,keep.id(),home,keep.authorMemberId());
        assertEquals(1,HouseholdDeletionReplay.replayFormerRecords(dataSource,false));
        assertEquals(1,HouseholdDeletionReplay.replayFormerRecords(dataSource,true));
        assertEquals(0,HouseholdDeletionReplay.replayFormerRecords(dataSource,true));
        assertTrue(objectNotes.list(home,owner,"fridge").isEmpty());
        assertEquals(keep.id(),objectNotes.list(other,owner,"fridge").getFirst().id());
        assertEquals(ExportState.CANCELLED,exports.status(home,owner,export.id()).state());
    }

    private CalendarInput event(String title,String body,long version){
        var date=LocalDate.of(2026,11,11);
        return new CalendarInput(title,body,"GENERAL",true,date,date,null,null,"Asia/Seoul",version);
    }

    @Test void formerCalendarOnlyReturnsOriginalAuthorsOwnUnsharedEditsAndRemovesRsvp(){
        var owner=user();var family=user();var h=house(owner,family);
        var original=calendar.save(h,family,null,event("내 일정","내가 쓴 내용",0));
        var edited=calendar.save(h,family,null,event("공동 편집 일정","초기 내용",0));
        var rewritten=calendar.save(h,owner,edited.id(),event("다른 사람이 편집","다른 사람의 내용",edited.version()));
        calendar.save(h,family,edited.id(),event("제목만 내가 다시 바꿈","다른 사람의 내용",rewritten.version()));
        var other=calendar.save(h,owner,null,event("대표 일정","대표의 내용",0));
        calendar.respond(h,owner,original.id(),new AttendanceInput(AttendanceStatus.ACCEPTED,0L));
        assertTrue(formerNotes.page(FormerRecordKind.CALENDAR,family,null,20).items().isEmpty());
        membership.leave(h,family,new LeaveHouse("calendar-leave"));
        var page=formerNotes.page(FormerRecordKind.CALENDAR,family,null,20);
        assertEquals(List.of(original.id()),page.items().stream().map(FormerRecord::id).toList());
        assertEquals("내 일정",page.items().getFirst().title());
        assertEquals("내가 쓴 내용",page.items().getFirst().body());
        assertThrows(ApiException.class,()->formerNotes.remove(FormerRecordKind.CALENDAR,family,edited.id(),edited.version()+1));
        assertThrows(ApiException.class,()->formerNotes.remove(FormerRecordKind.CALENDAR,family,other.id(),other.version()));
        formerNotes.remove(FormerRecordKind.CALENDAR,family,original.id(),original.version());
        assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM calendar_attendance WHERE event_id=?",Integer.class,original.id()));
        var date=LocalDate.of(2026,11,11);
        assertEquals(2,calendar.range(h,owner,date,date).size());
        assertTrue(formerNotes.page(FormerRecordKind.CALENDAR,family,null,20).items().isEmpty());
    }

    @Test void formerCalendarReplayDoesNotRestoreDeletedCalendarOrRsvp(){
        var owner=user();var family=user();var h=house(owner,family);
        var event=calendar.save(h,family,null,event("되살리면 안 되는 일정","",0));
        calendar.respond(h,owner,event.id(),new AttendanceInput(AttendanceStatus.ACCEPTED,0L));
        sql.update("""
            INSERT INTO former_record_deletion(record_kind,record_id,household_id,author_member_id,deleted_at)
            VALUES('CALENDAR',?,?,?,CURRENT_TIMESTAMP)
            """,event.id(),h,event.createdByMemberId());
        assertEquals(1,HouseholdDeletionReplay.replayFormerRecords(dataSource,false));
        assertEquals(1,HouseholdDeletionReplay.replayFormerRecords(dataSource,true));
        assertEquals(0,HouseholdDeletionReplay.replayFormerRecords(dataSource,true));
        assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM calendar_attendance WHERE event_id=?",Integer.class,event.id()));
    }

}
