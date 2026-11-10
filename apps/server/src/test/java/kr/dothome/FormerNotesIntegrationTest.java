package kr.dothome;

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
    @Autowired FormerNoteService formerNotes;
    @Autowired HouseholdExportService exports;
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
        assertTrue(formerNotes.page(family,null,20).items().isEmpty());
        membership.leave(old,family,new LeaveHouse("leave"));
        assertThrows(ApiException.class,()->homes.world(old,family));
        var first=formerNotes.page(family,null,1);
        assertEquals(1,first.items().size());
        var page=formerNotes.page(family,first.nextCursor(),1);
        assertNull(page.nextCursor());
        assertEquals(java.util.Set.of(note.id(),second.id()),java.util.Set.of(first.items().getFirst().id(),page.items().getFirst().id()));
        assertTrue(formerNotes.page(outsider,first.nextCursor(),20).items().isEmpty());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(family,other.id(),other.version())).status.value());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(family,active.id(),active.version())).status.value());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(owner,note.id(),note.version())).status.value());
        assertEquals(409,assertThrows(ApiException.class,()->formerNotes.remove(family,note.id(),note.version()+1)).status.value());

        var export=exports.start(old,owner,new ExportInput("snapshot-before-removal"));
        exports.generate(export.id());
        var token=exports.status(old,owner,export.id()).downloadToken();
        assertTrue(exports.download(old,owner,export.id(),token).contains(note.body()));
        formerNotes.remove(family,note.id(),note.version());
        formerNotes.remove(family,note.id(),note.version());
        assertEquals(404,assertThrows(ApiException.class,()->formerNotes.remove(outsider,note.id(),note.version())).status.value());
        assertEquals(List.of(second.id()),formerNotes.page(family,null,20).items().stream().map(FormerNote::id).toList());
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
        assertEquals(1,HouseholdDeletionReplay.replayFormerNotes(dataSource,false));
        assertEquals(1,HouseholdDeletionReplay.replayFormerNotes(dataSource,true));
        assertEquals(0,HouseholdDeletionReplay.replayFormerNotes(dataSource,true));
        assertTrue(objectNotes.list(home,owner,"fridge").isEmpty());
        assertEquals(keep.id(),objectNotes.list(other,owner,"fridge").getFirst().id());
        assertEquals(ExportState.CANCELLED,exports.status(home,owner,export.id()).state());
    }
}
