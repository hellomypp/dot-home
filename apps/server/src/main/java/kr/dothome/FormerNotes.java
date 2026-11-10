package kr.dothome;

import jakarta.validation.constraints.PositiveOrZero;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

record FormerNote(String id,String householdName,String body,Instant createdAt,long version) {}
record FormerNotePage(List<FormerNote> items,String nextCursor) {}
record FormerNoteTombstone(String id,String household,String author) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class FormerNoteService {
    private final ObjectNotes notes;
    private final Households houses;
    private final Members members;
    private final HomeService homes;
    private final JdbcTemplate sql;

    FormerNoteService(ObjectNotes notes,Households houses,Members members,HomeService homes,JdbcTemplate sql){
        this.notes=notes;this.houses=houses;this.members=members;this.homes=homes;this.sql=sql;
    }

    FormerNotePage page(String user,String cursor,int limit){
        if(limit<1||limit>100)throw ApiException.invalid("한 번에 1~100개의 기록을 불러올 수 있어요.");
        var before=cursor==null?new ObjectNoteCursor(Instant.EPOCH,""):ObjectNoteCursor.decode(cursor);
        var rows=notes.formerNotes(user,cursor==null,before.createdAt(),before.id(),Pageable.ofSize(limit+1));
        String next=null;
        if(rows.size()>limit){
            var last=rows.get(limit-1);
            next=ObjectNoteCursor.encode(last.createdAt(),last.id());
        }
        return new FormerNotePage(rows.stream().limit(limit).toList(),next);
    }

    void remove(String user,String id,long version){
        var houseId=notes.formerHouse(id,user).orElse(null);
        var house=houseId==null?null:houses.locked(houseId).orElse(null);
        var note=house==null?null:notes.findById(id).orElse(null);
        if(note==null){
            if(removedBy(user,id))return;
            throw ApiException.missing();
        }
        if(!note.householdId.equals(house.id)||!formerMember(note.authorMemberId,house.id,user))
            throw ApiException.missing();
        if(note.version!=version)throw ApiException.conflict("쪽지가 변경됐어요. 목록을 다시 확인해 주세요.");
        var row=new FormerNoteTombstone(note.id,house.id,note.authorMemberId);
        notes.delete(note);
        notes.flush();
        invalidateExports(sql,house.id);
        sql.update("""
            INSERT INTO former_record_deletion(record_kind,record_id,household_id,author_member_id,deleted_at)
            VALUES('OBJECT_NOTE',?,?,?,?)
            """,statement->{
                statement.setString(1,row.id());
                statement.setString(2,row.household());
                statement.setString(3,row.author());
                statement.setTimestamp(4,Timestamp.from(Instant.now()),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            });
        homes.emit(house,"OBJECT_NOTE_CHANGED",note.id);
    }

    private boolean removedBy(String user,String id){
        return sql.queryForList("""
            SELECT author_member_id,household_id FROM former_record_deletion
            WHERE record_kind='OBJECT_NOTE' AND record_id=?
            """,id).stream().anyMatch(row->formerMember((String)row.get("author_member_id"),(String)row.get("household_id"),user));
    }

    private boolean formerMember(String member,String house,String user){
        return members.findById(member).filter(m->!m.active&&m.householdId.equals(house)&&m.userId.equals(user)).isPresent();
    }

    static List<FormerNoteTombstone> restored(JdbcTemplate sql){
        return sql.query("""
            SELECT d.record_id,d.household_id,d.author_member_id FROM former_record_deletion d JOIN object_note n
              ON n.id=d.record_id AND n.household_id=d.household_id AND n.author_member_id=d.author_member_id
            WHERE d.record_kind='OBJECT_NOTE' ORDER BY d.household_id,d.record_id
            """,(row,index)->new FormerNoteTombstone(row.getString(1),row.getString(2),row.getString(3)));
    }

    static boolean removeRestored(JdbcTemplate sql,FormerNoteTombstone row){
        if(sql.queryForList("SELECT id FROM household WHERE id=? FOR UPDATE",String.class,row.household()).isEmpty())return false;
        int affected=sql.update("DELETE FROM object_note WHERE id=? AND household_id=? AND author_member_id=?",
            row.id(),row.household(),row.author());
        if(affected==0)return false;
        invalidateExports(sql,row.household());
        return true;
    }

    private static void invalidateExports(JdbcTemplate sql,String house){
        sql.update("UPDATE household_export SET export_state='CANCELLED',payload=NULL WHERE household_id=? AND export_state='READY'",house);
    }
}

@RestController @RequestMapping("/api/me/former-records/notes")
class FormerNoteController {
    private final FormerNoteService notes;
    FormerNoteController(FormerNoteService notes){this.notes=notes;}

    @GetMapping ResponseEntity<FormerNotePage> page(Principal user,@RequestParam(required=false) String cursor,
        @RequestParam(defaultValue="20") int limit){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notes.page(user.getName(),cursor,limit));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Principal user,@PathVariable UUID id,@RequestParam @PositiveOrZero long version){
        notes.remove(user.getName(),id.toString(),version);
    }
}
