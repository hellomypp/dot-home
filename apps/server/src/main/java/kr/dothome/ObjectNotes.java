package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

@Entity @Table(name="object_note")
class ObjectNote extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=24) String objectId;
    @Column(nullable=false,length=36) String authorMemberId;
    @Column(nullable=false,length=1000) String body;
    @Column(nullable=false,length=16) String state="ATTACHED";
    @Column(nullable=false) Instant createdAt=Instant.now();
    @Column(length=36) String takenByMemberId;
    Instant takenAt;
    @Version long version;
}
interface ObjectNotes extends JpaRepository<ObjectNote,String> {
    List<ObjectNote> findByHouseholdIdAndObjectIdAndStateOrderByCreatedAtDesc(String householdId,String objectId,String state);
    long countByHouseholdIdAndObjectIdAndState(String householdId,String objectId,String state);
    List<ObjectNote> findByHouseholdIdAndStateOrderByCreatedAtDesc(String householdId,String state);
}
record NoteInput(@NotBlank String body) {}
record NoteVersion(@Min(0) long version) {}
record ObjectNoteView(String id,String objectId,String authorMemberId,String body,String state,Instant createdAt,String takenByMemberId,Instant takenAt,long version) {}
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class ObjectNoteService {
    final HomeService homes;final ObjectNotes notes;
    ObjectNoteService(HomeService homes,ObjectNotes notes){this.homes=homes;this.notes=notes;}
    static void object(String id){if(!"fridge".equals(id))throw ApiException.invalid("아직 쪽지를 붙일 수 없는 사물이에요.");}
    List<ObjectNoteView> list(String h,String u,String object){homes.requireMember(h,u);object(object);return notes.findByHouseholdIdAndObjectIdAndStateOrderByCreatedAtDesc(h,object,"ATTACHED").stream().map(ObjectNoteService::view).toList();}
    List<ObjectNoteView> archive(String h,String u){homes.requireMember(h,u);return notes.findByHouseholdIdAndStateOrderByCreatedAtDesc(h,"TAKEN").stream().map(ObjectNoteService::view).toList();}
    ObjectNoteView create(String h,String u,String object,NoteInput input){
        object(object);
        Household house=homes.lock(h,u);
        Member me=homes.requireMember(h,u);
        String body=input.body()==null?"":input.body().strip();
        if(body.isEmpty()||body.codePointCount(0,body.length())>300)
            throw ApiException.invalid("쪽지는 1~300자로 입력해 주세요.");
        if(notes.countByHouseholdIdAndObjectIdAndState(h,object,"ATTACHED")>=10)
            throw ApiException.conflict("냉장고에 쪽지가 10장 붙어 있어요. 한 장을 떼거나 삭제해 주세요.");

        ObjectNote note=new ObjectNote();
        note.householdId=h;
        note.objectId=object;
        note.authorMemberId=me.id;
        note.body=body;
        notes.saveAndFlush(note);
        homes.emit(house,"OBJECT_NOTE_CHANGED",note.id);
        return view(note);
    }
    ObjectNoteView take(String h,String u,String id,long version){Household house=homes.lock(h,u);ObjectNote n=scoped(h,id);if(n.version!=version||!n.state.equals("ATTACHED"))throw ApiException.conflict("가족이 먼저 떼어 읽었어요. 쪽지를 다시 확인해주세요.");n.state="TAKEN";n.takenByMemberId=homes.requireMember(h,u).id;n.takenAt=Instant.now();notes.flush();homes.emit(house,"OBJECT_NOTE_CHANGED",n.id);return view(n);}
    void delete(String h,String u,String id,long version){Household house=homes.lock(h,u);Member me=homes.requireMember(h,u);ObjectNote n=scoped(h,id);if(!n.authorMemberId.equals(me.id)&&!house.ownerId.equals(u))throw ApiException.forbidden();if(n.version!=version)throw ApiException.conflict("쪽지가 변경됐어요. 다시 확인해주세요.");notes.delete(n);notes.flush();homes.emit(house,"OBJECT_NOTE_CHANGED",id);}
    ObjectNote scoped(String h,String id){ObjectNote n=notes.findById(id).orElseThrow(ApiException::missing);if(!n.householdId.equals(h))throw ApiException.forbidden();return n;}
    static ObjectNoteView view(ObjectNote n){return new ObjectNoteView(n.id,n.objectId,n.authorMemberId,n.body,n.state,n.createdAt,n.takenByMemberId,n.takenAt,n.version);}
}
@RestController @RequestMapping("/api/households/{h}")
class ObjectNoteController {
    final ObjectNoteService service;
    ObjectNoteController(ObjectNoteService service){this.service=service;}
    @GetMapping("/objects/{object}/notes") List<ObjectNoteView> list(@PathVariable String h,@PathVariable String object,Principal p){return service.list(h,p.getName(),object);}
    @PostMapping("/objects/{object}/notes") ObjectNoteView create(@PathVariable String h,@PathVariable String object,Principal p,@Valid @RequestBody NoteInput input){return service.create(h,p.getName(),object,input);}
    @GetMapping("/notes/archive") List<ObjectNoteView> archive(@PathVariable String h,Principal p){return service.archive(h,p.getName());}
    @PostMapping("/notes/{id}/take") ObjectNoteView take(@PathVariable String h,@PathVariable String id,Principal p,@Valid @RequestBody NoteVersion input){return service.take(h,p.getName(),id,input.version());}
    @DeleteMapping("/notes/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void delete(@PathVariable String h,@PathVariable String id,@RequestParam long version,Principal p){service.delete(h,p.getName(),id,version);}
}
