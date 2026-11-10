package kr.dothome;

import jakarta.validation.constraints.PositiveOrZero;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

enum FormerRecordKind {
    OBJECT_NOTE("object_note","author_member_id","NULL","r.body","","OBJECT_NOTE_CHANGED"),
    CALENDAR("household_calendar_event","created_by_member_id","r.title","r.description",
        "AND r.updated_by_member_id=m.id AND r.title_editor_member_id=m.id AND r.description_editor_member_id=m.id",
        "CALENDAR_EVENT_CHANGED");

    final String table,author,title,body,condition,signal;
    FormerRecordKind(String table,String author,String title,String body,String condition,String signal){
        this.table=table;this.author=author;this.title=title;this.body=body;this.condition=condition;this.signal=signal;
    }

    static FormerRecordKind path(String resource){
        return switch(resource){
            case "notes" -> OBJECT_NOTE;
            case "calendar" -> CALENDAR;
            default -> throw ApiException.missing();
        };
    }
}

record FormerRecord(String id,String householdName,String title,String body,Instant createdAt,long version) {}
record FormerRecordPage(List<FormerRecord> items,String nextCursor) {}
record FormerRecordTombstone(FormerRecordKind kind,String id,String household,String author) {}
record OwnedFormerRecord(String household,String author,long version) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class FormerRecordService {
    private final Households houses;
    private final Members members;
    private final HomeService homes;
    private final JdbcTemplate sql;

    FormerRecordService(Households houses,Members members,HomeService homes,JdbcTemplate sql){
        this.houses=houses;this.members=members;this.homes=homes;this.sql=sql;
    }

    FormerRecordPage page(FormerRecordKind kind,String user,String cursor,int limit){
        if(limit<1||limit>100)throw ApiException.invalid("한 번에 1~100개의 기록을 불러올 수 있어요.");
        var before=cursor==null?new ObjectNoteCursor(Instant.EPOCH,""):ObjectNoteCursor.decode(cursor);
        var rows=sql.query("""
            SELECT r.id,h.name,%s,%s,r.created_at,r.version FROM %s r
            JOIN membership m ON m.id=r.%s AND m.household_id=r.household_id
            JOIN household h ON h.id=r.household_id
            WHERE m.user_id=? AND m.active=FALSE %s
              AND (? OR r.created_at<? OR (r.created_at=? AND r.id<?))
            ORDER BY r.created_at DESC,r.id DESC LIMIT ?
            """.formatted(kind.title,kind.body,kind.table,kind.author,kind.condition),statement->{
                statement.setString(1,user);
                statement.setBoolean(2,cursor==null);
                var timestamp=Timestamp.from(before.createdAt());
                statement.setTimestamp(3,timestamp,utc());
                statement.setTimestamp(4,timestamp,utc());
                statement.setString(5,before.id());
                statement.setInt(6,limit+1);
            },(row,index)->new FormerRecord(row.getString(1),row.getString(2),row.getString(3),row.getString(4),
                row.getTimestamp(5,utc()).toInstant(),row.getLong(6)));
        String next=null;
        if(rows.size()>limit){
            var last=rows.get(limit-1);
            next=ObjectNoteCursor.encode(last.createdAt(),last.id());
        }
        return new FormerRecordPage(rows.stream().limit(limit).toList(),next);
    }

    void remove(FormerRecordKind kind,String user,String id,long version){
        var found=owned(kind,user,id);
        var house=found==null?null:houses.locked(found.household()).orElse(null);
        var record=house==null?null:owned(kind,user,id);
        if(record==null){
            if(removedBy(kind,user,id))return;
            throw ApiException.missing();
        }
        if(!record.household().equals(house.id))throw ApiException.missing();
        if(record.version()!=version)throw ApiException.conflict("기록이 변경됐어요. 목록을 다시 확인해 주세요.");
        var row=new FormerRecordTombstone(kind,id,record.household(),record.author());
        if(!removeMatchingRecord(sql,row))throw ApiException.missing();
        sql.update("""
            INSERT INTO former_record_deletion(record_kind,record_id,household_id,author_member_id,deleted_at)
            VALUES(?,?,?,?,?)
            """,statement->{
                statement.setString(1,row.kind().name());
                statement.setString(2,row.id());
                statement.setString(3,row.household());
                statement.setString(4,row.author());
                statement.setTimestamp(5,Timestamp.from(Instant.now()),utc());
            });
        homes.emit(house,kind.signal,id);
    }

    private OwnedFormerRecord owned(FormerRecordKind kind,String user,String id){
        return sql.query("""
            SELECT r.household_id,m.id,r.version FROM %s r
            JOIN membership m ON m.id=r.%s AND m.household_id=r.household_id
            WHERE r.id=? AND m.user_id=? AND m.active=FALSE %s
            """.formatted(kind.table,kind.author,kind.condition),
            (row,index)->new OwnedFormerRecord(row.getString(1),row.getString(2),row.getLong(3)),id,user)
            .stream().findFirst().orElse(null);
    }

    private boolean removedBy(FormerRecordKind kind,String user,String id){
        return sql.query("""
            SELECT author_member_id,household_id FROM former_record_deletion WHERE record_kind=? AND record_id=?
            """,(row,index)->new FormerRecordTombstone(kind,id,row.getString(2),row.getString(1)),kind.name(),id)
            .stream().anyMatch(row->members.findById(row.author())
                .filter(member->!member.active&&member.householdId.equals(row.household())&&member.userId.equals(user)).isPresent());
    }

    static List<FormerRecordTombstone> restored(JdbcTemplate sql,FormerRecordKind kind){
        return sql.query("""
            SELECT d.record_id,d.household_id,d.author_member_id FROM former_record_deletion d JOIN %s r
              ON r.id=d.record_id AND r.household_id=d.household_id AND r.%s=d.author_member_id
            WHERE d.record_kind=? ORDER BY d.household_id,d.record_id
            """.formatted(kind.table,kind.author),
            (row,index)->new FormerRecordTombstone(kind,row.getString(1),row.getString(2),row.getString(3)),kind.name());
    }

    static boolean removeRestored(JdbcTemplate sql,FormerRecordTombstone row){
        if(sql.queryForList("SELECT id FROM household WHERE id=? FOR UPDATE",String.class,row.household()).isEmpty())return false;
        return removeMatchingRecord(sql,row);
    }

    private static boolean removeMatchingRecord(JdbcTemplate sql,FormerRecordTombstone row){
        var kind=row.kind();
        String matching=String.format("id=? AND household_id=? AND %s=?",kind.author);
        if(kind==FormerRecordKind.CALENDAR)
            sql.update("DELETE FROM calendar_attendance WHERE event_id IN (SELECT id FROM household_calendar_event WHERE "+matching+")",
                row.id(),row.household(),row.author());
        int affected=sql.update("DELETE FROM "+kind.table+" WHERE "+matching,row.id(),row.household(),row.author());
        if(affected==0)return false;
        sql.update("UPDATE household_export SET export_state='CANCELLED',payload=NULL WHERE household_id=? AND export_state='READY'",row.household());
        return true;
    }

    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}

@RestController @RequestMapping("/api/me/former-records/{resource}")
class FormerRecordController {
    private final FormerRecordService records;
    FormerRecordController(FormerRecordService records){this.records=records;}

    @GetMapping ResponseEntity<FormerRecordPage> page(Principal user,@PathVariable String resource,@RequestParam(required=false) String cursor,
        @RequestParam(defaultValue="20") int limit){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(records.page(FormerRecordKind.path(resource),user.getName(),cursor,limit));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Principal user,@PathVariable String resource,@PathVariable UUID id,@RequestParam @PositiveOrZero long version){
        records.remove(FormerRecordKind.path(resource),user.getName(),id.toString(),version);
    }
}
