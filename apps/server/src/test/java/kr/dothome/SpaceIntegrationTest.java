package kr.dothome;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class SpaceIntegrationTest {
    @Autowired HomeService homes; @Autowired IdentityService identities; @Autowired HousewarmingService visits; @Autowired JdbcTemplate jdbc;
    String user(){return identities.resolve("google",UUID.randomUUID().toString(),"검증").id;}
    @Test void sharedDesignPersistsAndVisitorsGetOnlyPublicDesign(){
        String owner=user(),family=user(),visitor=user();
        var world=homes.create(owner,new Api.CreateHouse("공유 공간","대표","v2:adult_female","sunny_apartment"));
        String h=world.household().id();assertEquals("sunny_apartment",world.design().presetId());
        homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","v1:3212"));
        var changed=homes.design(h,owner,new SpaceInput("brick_loft",0,Map.of("sofa","blue","table","round")));
        assertEquals(1,changed.design().version());assertTrue(changed.revision()>world.revision());
        assertEquals(changed.design(),homes.world(h,family).design());
        var next=homes.design(h,owner,new SpaceInput("garden_home",1,changed.design().variants()));
        assertEquals("blue",next.design().variants().get("sofa"));
        var link=visits.create(h,owner).url().substring(7);
        assertEquals(next.design(),visits.enter(link,visitor).design());
        assertEquals(2,homes.world(h,owner).members().size());
        assertThrows(ApiException.class,()->homes.design(h,visitor));
        assertThrows(ApiException.class,()->homes.design(h,family,new SpaceInput("garden_home",2,Map.of())));
        assertThrows(ApiException.class,()->homes.design(h,user()));
        assertThrows(ApiException.class,()->homes.design(h,owner,new SpaceInput("garden_home",0,Map.of())));
        assertThrows(ApiException.class,()->homes.design(h,owner,new SpaceInput("bad",2,Map.of())));
        assertThrows(ApiException.class,()->homes.design(h,owner,new SpaceInput("garden_home",2,Map.of("fridge","secret"))));
        assertEquals(next.design(),homes.design(h,owner));
    }
    @Test void simultaneousSavesHaveOneWinner()throws Exception{
        String owner=user(),h=homes.create(owner,new Api.CreateHouse("경합","대표","sage")).household().id();
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Boolean> save=()->{start.await();try{homes.design(h,owner,new SpaceInput("brick_loft",0,Map.of()));return true;}catch(ApiException e){return false;}};
            var a=pool.submit(save);var b=pool.submit(save);start.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,homes.design(h,owner).version());
    }
    @Test void legacyCoordinatesAndAppearanceAreNotOverwritten(){
        String owner=user(),h=homes.create(owner,new Api.CreateHouse("기존 집","대표","v1:3212")).household().id();
        jdbc.update("UPDATE membership SET avatar_space='legacy',x=320,y=288 WHERE household_id=?",h);
        var m=homes.world(h,owner).members().getFirst();assertEquals(224,m.x());assertEquals(272,m.y());assertEquals("v1:3212",m.appearance());
        assertEquals(320,jdbc.queryForObject("SELECT x FROM membership WHERE household_id=?",Double.class,h));
        homes.profile(h,owner,new Api.Profile("새 이름","v2:child"));assertEquals("v2:child",homes.world(h,owner).members().getFirst().appearance());
        assertThrows(ApiException.class,()->homes.profile(h,owner,new Api.Profile("새 이름","v2:unknown")));
    }
    @Test void warmWoodDesignUsesSharedCollisionAndPreservesFamilyAndVisitDesign(){
        String owner=user(),family=user(),visitor=user(),preset="warm-wood-home";
        var created=homes.create(owner,new Api.CreateHouse("따뜻한 집","대표","sage",preset));
        String id=created.household().id();
        var representative=created.members().getFirst();
        assertEquals(preset,created.design().presetId());
        assertEquals(224,representative.x());
        assertEquals(260,representative.y());
        assertTrue(Spaces.walkable(representative.x(),representative.y(),preset));
        assertFalse(Spaces.walkable(338,133,preset));
        assertTrue(Spaces.walkable(338,148,preset));
        for(var object:Spaces.map(preset).get("objects"))if(object.get("interactive").asBoolean()){
            var anchor=object.get("interactionAnchor");
            assertTrue(Spaces.walkable(anchor.get(0).asDouble(),anchor.get(1).asDouble(),preset));
        }
        homes.accept(homes.invite(id,owner).token(),family,new Api.Accept("가족","sage"));
        assertEquals(2,homes.world(id,family).members().size());
        var link=visits.create(id,owner).url().substring(7);
        assertEquals(preset,visits.enter(link,visitor).design().presetId());
    }
    @Test void canonicalAnchorsAndFootCollisionMatchAllMaps(){
        for(var preset:List.of("garden_home","sunny_apartment","brick_loft")){
            assertTrue(Spaces.walkable(224,272,preset));assertFalse(Spaces.walkable(-1,100,preset));assertFalse(Spaces.walkable(Double.NaN,100,preset));
            for(var object:Spaces.map(preset).get("objects"))if(object.get("interactive").asBoolean()){
                var anchor=object.get("interactionAnchor");assertTrue(Spaces.walkable(anchor.get(0).asDouble(),anchor.get(1).asDouble(),preset),preset+"/"+object.get("id"));
            }
        }
    }
}
