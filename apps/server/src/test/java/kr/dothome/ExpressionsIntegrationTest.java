package kr.dothome;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class ExpressionsIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired ObjectMapper json;
    @Autowired Members members;
    @Autowired MemberExpressions rows;
    @Autowired MovementService movement;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"검증 가족").id;}
    Api.World house(String owner){return homes.create(owner,new Api.CreateHouse("검증 집","대표","sage"));}
    ExpressionPatch patch(String id,String changes){
        Map<String,JsonNode> fields=new TreeMap<>();
        json.readTree(changes).properties().forEach(e->fields.put(e.getKey(),e.getValue()));
        return new ExpressionPatch(id,fields);
    }
    @Test void expressionsHaveIndependentClocksAndClears(){
        String owner=user(),h=house(owner).household().id();
        var original=homes.expressions(h,owner,patch("initial","""
            {"mood":{"value":"CALM","version":0},"food":{"value":"수프","version":0}}
            """));
        var mood=original.expressions().get("mood");
        var changed=homes.expressions(h,owner,patch("food-only","""
            {"food":{"value":"김밥","version":1}}
            """));
        assertEquals(mood,changed.expressions().get("mood"));
        assertEquals("김밥",changed.expressions().get("food").value());
        assertEquals(360,ChronoUnit.MINUTES.between(changed.expressions().get("food").updatedAt(),changed.expressions().get("food").expiresAt()));
        var clear=homes.expressions(h,owner,patch("clear-food","""
            {"food":{"value":null,"version":2}}
            """));
        assertNull(clear.expressions().get("food").value());
        assertEquals(mood,clear.expressions().get("mood"));
        assertEquals(Set.of("mood"),homes.world(h,owner).members().getFirst().currentState().keySet());
    }
    @Test void requestReplayIsStableAndConflictsRollBackWholeBatch(){
        String owner=user(),h=house(owner).household().id();
        var input=patch("same","""
            {"thought":{"value":"안녕","version":0},"mood":{"value":"HAPPY","version":0}}
            """);
        var created=homes.expressions(h,owner,input);
        assertEquals(created,homes.expressions(h,owner,input));
        assertEquals(409,assertThrows(ApiException.class,()->homes.expressions(h,owner,patch("same","{}"))).status.value());
        var stale=patch("stale","""
            {"food":{"value":"국수","version":0},"mood":{"value":"WORRIED","version":0}}
            """);
        assertEquals(409,assertThrows(ApiException.class,()->homes.expressions(h,owner,stale)).status.value());
        var unchanged=homes.expressions(h,owner,patch("unchanged","""
            {"mood":{"value":"HAPPY","version":1}}
            """));
        assertEquals(created.revision(),unchanged.revision());
        assertNull(unchanged.expressions().get("food").value());
        assertEquals(403,assertThrows(ApiException.class,()->homes.expressions(h,user())).status.value());
        assertEquals(400,assertThrows(ApiException.class,()->homes.expressions(h,owner,patch("missing","""
            {"food":{"version":0}}
            """))).status.value());
    }
    @Test void virtualWorkShiftRespectsHouseholdConsentAndBlocksDirectControl(){
        String owner=user(),family=user();
        var created=house(owner);
        String h=created.household().id(), miner=created.meMemberId();
        homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        var working=homes.workShift(h,owner,true);
        assertEquals("office",working.members().stream().filter(m->m.id().equals(miner)).findFirst().orElseThrow().place());
        assertEquals(working.revision(),homes.workShift(h,owner,true).revision());
        assertEquals("outside",homes.world(h,family).members().stream().filter(m->m.id().equals(miner)).findFirst().orElseThrow().place());
        assertThrows(ApiException.class,()->movement.apply(h,owner,new Api.Intent("mine-move","MOVE_INTENT","office",320d,288d,null)));
        assertThrows(ApiException.class,()->movement.apply(h,owner,new Api.Intent("mine-interact","INTERACT_INTENT","office",null,null,"table")));
        homes.expressions(h,owner,patch("consent","""
            {"sharing":{"enabled":true,"version":0}}
            """));
        var visible=homes.world(h,family).members().stream().filter(m->m.id().equals(miner)).findFirst().orElseThrow();
        assertEquals("office",visible.place());
        assertEquals("working",visible.action());
        assertNull(visible.sharedPlace().place());
        homes.expressions(h,owner,patch("revoke","""
            {"sharing":{"enabled":false,"version":1}}
            """));
        assertEquals("outside",homes.world(h,family).members().stream().filter(m->m.id().equals(miner)).findFirst().orElseThrow().place());
        assertEquals("home",homes.workShift(h,owner,false).members().stream().filter(m->m.id().equals(miner)).findFirst().orElseThrow().place());
    }
    @Test void manualPlaceExpiresAndRevocationHidesLocationWithoutClearingExpressions(){
        String owner=user(),guest=user();
        var created=house(owner);
        String h=created.household().id();
        homes.accept(homes.invite(h,owner).token(),guest,new Api.Accept("가족","sage"));
        assertEquals("UNSHARED",homes.world(h,guest).members().stream()
            .filter(m->m.id().equals(created.meMemberId())).findFirst().orElseThrow().sharedPlace().status());
        assertEquals(400,assertThrows(ApiException.class,()->homes.expressions(h,owner,patch("without-permission","""
            {"place":{"value":"OFFICE","version":0}}
            """))).status.value());
        var start=homes.expressions(h,owner,patch("manual","""
            {"sharing":{"enabled":true,"version":0},"place":{"value":"OFFICE","version":0,"ttlMinutes":15},
             "thought":{"value":"잠시 쉬어요","version":0}}
            """));
        assertEquals("OFFICE",start.place().value());
        assertEquals(15,ChronoUnit.MINUTES.between(start.place().updatedAt(),start.place().expiresAt()));
        var before=homes.world(h,guest).members().stream().filter(m->m.id().equals(created.meMemberId())).findFirst().orElseThrow();
        assertEquals("OFFICE",before.sharedPlace().place());
        var stored=rows.findByMemberIdIn(List.of(created.meMemberId())).stream().filter(r->r.fieldName.equals("place")).findFirst().orElseThrow();
        stored.expiresAt=Instant.now().minusSeconds(1);
        rows.saveAndFlush(stored);
        var expired=homes.world(h,guest).members().stream().filter(m->m.id().equals(created.meMemberId())).findFirst().orElseThrow();
        assertEquals("UNKNOWN",expired.sharedPlace().status());
        assertEquals("OFFICE",expired.sharedPlace().lastPlace());
        assertNull(expired.sharedPlace().place());
        assertEquals("outside",expired.place());
        var revoked=homes.expressions(h,owner,patch("stop-sharing","""
            {"sharing":{"enabled":false,"version":1}}
            """));
        assertFalse(revoked.sharing().enabled());
        assertNull(revoked.place().value());
        assertEquals("잠시 쉬어요",revoked.expressions().get("thought").value());
        var hidden=homes.world(h,guest).members().stream().filter(m->m.id().equals(created.meMemberId())).findFirst().orElseThrow().sharedPlace();
        assertEquals("UNSHARED",hidden.status());
        assertNull(hidden.lastPlace());
        assertNull(hidden.sharedAt());
    }
    @Test void legacyFieldsAreReadAndMissingLegacyPatchFieldsRemainUntouched(){
        String owner=user();
        var created=house(owner);
        String h=created.household().id();
        var member=members.findById(created.meMemberId()).orElseThrow();
        member.mood="기존 기분";
        member.statusExpiresAt=Instant.now().plusSeconds(600);
        members.saveAndFlush(member);
        var old=homes.expressions(h,owner).expressions().get("mood");
        assertEquals("기존 기분",old.value());
        homes.status(h,owner,new Api.Status(null,null,"새 음식",null,60));
        assertEquals(old,homes.expressions(h,owner).expressions().get("mood"));
        var food=rows.findByMemberIdIn(List.of(member.id)).getFirst();
        food.expiresAt=Instant.now().minusSeconds(1);
        rows.saveAndFlush(food);
        assertEquals(Set.of("mood"),homes.world(h,owner).members().getFirst().currentState().keySet());
        assertNull(homes.expressions(h,owner).expressions().get("food").value());
    }
}
