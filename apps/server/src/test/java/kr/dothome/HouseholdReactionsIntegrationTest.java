package kr.dothome;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;
import java.util.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class HouseholdReactionsIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired HouseholdReactionService reactions;
    @Autowired ObjectNoteService notes;
    @Autowired MemberExpressions expressions;
    @Autowired HouseholdRequestService requests;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"검증 가족").id;}
    @Test void reactionsDeduplicateAndNeverCreateTasks(){
        String owner=user(),guest=user(),outsider=user();
        var world=homes.create(owner,new Api.CreateHouse("검증 집","대표","sage"));
        String house=world.household().id();
        homes.accept(homes.invite(house,owner).token(),guest,new Api.Accept("가족","sage"));
        var note=notes.create(house,owner,"fridge",new NoteInput("잘 다녀와"));
        var input=new ReactionInput("first",ReactionSource.NOTE,note.id(),ReactionCode.THANKS);
        var created=reactions.create(house,guest,input);
        assertEquals(created,reactions.create(house,guest,input));
        assertEquals(created,reactions.create(house,guest,new ReactionInput("retry",ReactionSource.NOTE,note.id(),ReactionCode.THANKS)));
        assertEquals(409,assertThrows(ApiException.class,()->reactions.create(house,guest,
            new ReactionInput("first",ReactionSource.NOTE,note.id(),ReactionCode.CHEER))).status.value());
        assertEquals(Map.of(ReactionCode.THANKS,1L),reactions.read(house,owner,ReactionSource.NOTE,note.id()).counts());
        assertTrue(reactions.read(house,owner,ReactionSource.NOTE,note.id()).mine().isEmpty());
        assertTrue(requests.list(house,owner,null,null,20).items().isEmpty());
        assertEquals(403,assertThrows(ApiException.class,()->reactions.read(house,outsider,ReactionSource.NOTE,note.id())).status.value());
        assertEquals(403,assertThrows(ApiException.class,()->reactions.delete(house,owner,created.id())).status.value());
        reactions.delete(house,guest,created.id());
        assertTrue(reactions.read(house,owner,ReactionSource.NOTE,note.id()).counts().isEmpty());
        notes.delete(house,owner,note.id(),note.version());
        assertEquals(403,assertThrows(ApiException.class,()->reactions.create(house,guest,input)).status.value());
    }
    @Test void foodParticipationRequiresTheCurrentExpressionVersion(){
        String owner=user(),guest=user();
        var world=homes.create(owner,new Api.CreateHouse("식탁 집","대표","sage"));
        String house=world.household().id();
        homes.accept(homes.invite(house,owner).token(),guest,new Api.Accept("가족","sage"));
        homes.status(house,owner,new Api.Status(null,null,"수프","식사 전이에요",30));
        String base=world.meMemberId();
        assertEquals(400,assertThrows(ApiException.class,()->reactions.create(house,guest,
            new ReactionInput("wrong",ReactionSource.EXPRESSION,base+".thought.1",ReactionCode.SHARE_MEAL))).status.value());
        String food=base+".food.1";
        var joined=reactions.create(house,guest,new ReactionInput("join",ReactionSource.EXPRESSION,food,ReactionCode.SHARE_MEAL));
        assertEquals(ReactionCode.SHARE_MEAL,joined.code());
        var stored=expressions.findByMemberIdIn(List.of(base)).stream().filter(r->r.fieldName.equals("food")).findFirst().orElseThrow();
        stored.expiresAt=Instant.now().minusSeconds(1);
        expressions.saveAndFlush(stored);
        assertEquals(410,assertThrows(ApiException.class,()->reactions.read(house,guest,ReactionSource.EXPRESSION,food)).status.value());
        reactions.delete(house,guest,joined.id());
        assertTrue(requests.list(house,owner,null,null,20).items().isEmpty());
    }
}
