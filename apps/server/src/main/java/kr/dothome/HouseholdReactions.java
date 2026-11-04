package kr.dothome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

enum ReactionSource { EXPRESSION, REQUEST, NOTE }
enum ReactionCode { THANKS, CHEER, ACK, SHARE_MEAL }

@Entity @Table(name="household_reaction")
class HouseholdReaction extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String authorMemberId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) ReactionSource sourceType;
    @Column(nullable=false,length=128) String sourceId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) ReactionCode reactionCode;
    @Column(nullable=false) Instant createdAt;
}
interface HouseholdReactions extends JpaRepository<HouseholdReaction,String> {
    List<HouseholdReaction> findByHouseholdIdAndSourceTypeAndSourceId(String h,ReactionSource type,String id);
    Optional<HouseholdReaction> findByIdAndHouseholdIdAndAuthorMemberId(String id,String h,String member);
}
record ReactionInput(@NotBlank @Size(max=80) String requestId,@NotNull ReactionSource sourceType,
    @NotBlank @Size(max=128) String sourceId,@NotNull ReactionCode code) {}
record ReactionView(String id,ReactionCode code,Instant createdAt) {}
record ReactionSummary(Map<ReactionCode,Long> counts,List<ReactionView> mine) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class HouseholdReactionService {
    private final HomeService homes;
    private final Members members;
    private final ObjectNotes notes;
    private final HouseholdRequests requests;
    private final ExpressionStore expressions;
    private final HouseholdReactions reactions;
    private final HouseholdCommandReceipts receipts;
    HouseholdReactionService(HomeService homes,Members members,ObjectNotes notes,HouseholdRequests requests,
        ExpressionStore expressions,HouseholdReactions reactions,HouseholdCommandReceipts receipts){
        this.homes=homes;this.members=members;this.notes=notes;this.requests=requests;
        this.expressions=expressions;this.reactions=reactions;this.receipts=receipts;
    }
    ReactionSummary read(String house,String user,ReactionSource type,String sourceId){
        Member member=homes.requireMember(house,user);
        validateSource(house,type,sourceId,Instant.now());
        var counts=new EnumMap<ReactionCode,Long>(ReactionCode.class);
        var mine=new ArrayList<ReactionView>();
        for(var reaction:reactions.findByHouseholdIdAndSourceTypeAndSourceId(house,type,sourceId)){
            counts.merge(reaction.reactionCode,1L,Long::sum);
            if(reaction.authorMemberId.equals(member.id))mine.add(view(reaction));
        }
        return new ReactionSummary(counts,mine);
    }
    ReactionView create(String house,String user,ReactionInput input){
        Household h=homes.lock(house,user);
        Member member=homes.requireMember(house,user);
        Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        String field=validateSource(house,input.sourceType(),input.sourceId(),now);
        if(input.code()==ReactionCode.SHARE_MEAL&&!"food".equals(field))
            throw ApiException.invalid("같이 먹기는 음식 표현에만 남길 수 있어요.");
        String hash=HomeService.hash(input.sourceType()+"\n"+input.sourceId()+"\n"+input.code());
        return receipts.once(member,"reaction",input.requestId(),hash,now,ReactionView.class,()->{
            for(var existing:reactions.findByHouseholdIdAndSourceTypeAndSourceId(house,input.sourceType(),input.sourceId())){
                if(existing.authorMemberId.equals(member.id)&&existing.reactionCode==input.code())return view(existing);
            }
            var reaction=new HouseholdReaction();
            reaction.householdId=house;reaction.authorMemberId=member.id;reaction.sourceType=input.sourceType();
            reaction.sourceId=input.sourceId();reaction.reactionCode=input.code();reaction.createdAt=now;
            reactions.save(reaction);
            homes.emit(h,"HOUSEHOLD_REACTION_CHANGED",Map.of("sourceType",input.sourceType(),"sourceId",input.sourceId()));
            return view(reaction);
        });
    }
    void delete(String house,String user,String id){
        Household h=homes.lock(house,user);
        Member member=homes.requireMember(house,user);
        var reaction=reactions.findByIdAndHouseholdIdAndAuthorMemberId(id,house,member.id).orElseThrow(ApiException::forbidden);
        reactions.delete(reaction);
        homes.emit(h,"HOUSEHOLD_REACTION_CHANGED",Map.of("sourceType",reaction.sourceType,"sourceId",reaction.sourceId));
    }
    private String validateSource(String house,ReactionSource type,String id,Instant now){
        switch(type){
            case NOTE -> {
                if(notes.findById(id).filter(note->note.householdId.equals(house)).isEmpty())throw ApiException.forbidden();
            }
            case REQUEST -> {
                if(requests.findByIdAndHouseholdId(id,house).isEmpty())throw ApiException.forbidden();
            }
            case EXPRESSION -> {
                String[] parts=id.split("\\.",-1);
                if(parts.length!=3||!parts[0].matches("[a-f0-9-]{36}")||
                    !Set.of("thought","food","mood","physical","availability").contains(parts[1])||
                    !parts[2].matches("[0-9]{1,19}"))
                    throw ApiException.invalid("반응을 남길 표현을 확인해 주세요.");
                var author=members.findById(parts[0]).filter(member->member.active&&member.householdId.equals(house))
                    .orElseThrow(ApiException::forbidden);
                var current=expressions.state(author,expressions.load(author),now,false).get(parts[1]);
                if(current==null||!Long.toString(current.version()).equals(parts[2]))
                    throw new ApiException(HttpStatus.GONE,"EXPIRED","이미 만료되거나 바뀐 표현이에요.");
                return parts[1];
            }
        }
        return null;
    }
    private static ReactionView view(HouseholdReaction row){return new ReactionView(row.id,row.reactionCode,row.createdAt);}
}

@RestController @RequestMapping("/api/households/{house}/reactions")
class HouseholdReactionController {
    private final HouseholdReactionService reactions;
    HouseholdReactionController(HouseholdReactionService reactions){this.reactions=reactions;}
    @GetMapping ReactionSummary read(@PathVariable String house,Principal p,@RequestParam ReactionSource sourceType,
        @RequestParam @NotBlank @Size(max=128) String sourceId){
        return reactions.read(house,p.getName(),sourceType,sourceId);
    }
    @PostMapping ReactionView create(@PathVariable String house,Principal p,@Valid @RequestBody ReactionInput input){
        return reactions.create(house,p.getName(),input);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String house,@PathVariable String id,Principal p){reactions.delete(house,p.getName(),id);}
}
