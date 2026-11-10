package kr.dothome;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class SpaceIntegrationTest {
    @Autowired HomeService homes; @Autowired IdentityService identities; @Autowired HousewarmingService visits; @Autowired JdbcTemplate jdbc;
    @Autowired SpaceEditing editing;
    @Autowired OwnershipService ownership;
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
    @Test void privateDraftCommitsOnceAndHasARecoverableReceipt(){
        String owner=user(),family=user(),h=homes.create(owner,new Api.CreateHouse("편집 집","대표","sage")).household().id();
        homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        var initial=homes.world(h,family);
        var start=new SpaceEditStart("start",null);
        var session=editing.start(h,owner,start);
        assertEquals(session,editing.start(h,owner,start));
        assertEquals(403,assertThrows(ApiException.class,()->editing.current(h,family)).status.value());
        assertEquals(403,assertThrows(ApiException.class,()->editing.start(h,user(),start)).status.value());
        var input=new SpaceInput("brick_loft",0,Map.of("fridge","star"));
        assertEquals("EDIT_BUSY",assertThrows(ApiException.class,()->homes.design(h,owner,input)).code);
        var saved=editing.draft(h,owner,session.id(),new SpaceEditDraft(0L,input));
        assertEquals(1,saved.draftVersion());
        assertEquals(1,editing.draft(h,owner,session.id(),new SpaceEditDraft(0L,input)).draftVersion());
        assertEquals(409,assertThrows(ApiException.class,()->editing.draft(h,owner,session.id(),
            new SpaceEditDraft(0L,new SpaceInput("garden_home",0,Map.of())))).status.value());
        assertEquals(initial.design(),homes.world(h,family).design());
        assertEquals(initial.revision(),homes.world(h,family).revision());
        var command=new SpaceEditCommit("commit",0L,1L);
        assertEquals(1,editing.commit(h,owner,session.id(),command).committedVersion());
        expireLease(session.id(),Instant.now().minusSeconds(2));
        assertEquals(1,editing.commit(h,owner,session.id(),command).committedVersion());
        assertEquals(1,editing.result(h,owner,session.id(),"commit").committedVersion());
        assertEquals(404,assertThrows(ApiException.class,()->editing.result(h,owner,session.id(),"other")).status.value());
        assertEquals(409,assertThrows(ApiException.class,()->editing.commit(h,owner,session.id(),new SpaceEditCommit("another",0L,1L))).status.value());
        var updated=homes.world(h,family);
        assertEquals("brick_loft",updated.design().presetId());
        assertEquals(initial.revision()+1,updated.revision());
    }
    @Test void expiredSessionCannotSaveAndDraftCanRecoverOnlyAgainstItsBase(){
        String owner=user(),h=homes.create(owner,new Api.CreateHouse("복구 집","대표","sage")).household().id();
        var session=editing.start(h,owner,new SpaceEditStart("one",null));
        var changed=new SpaceInput("sunny_apartment",0,Map.of());
        editing.draft(h,owner,session.id(),new SpaceEditDraft(0L,changed));
        expireLease(session.id(),Instant.now().minusSeconds(2));
        assertEquals("EDIT_EXPIRED",assertThrows(ApiException.class,()->editing.heartbeat(h,owner,session.id())).code);
        assertEquals(410,assertThrows(ApiException.class,()->editing.commit(h,owner,session.id(),new SpaceEditCommit("late",0L,1L))).status.value());
        assertEquals("RECOVERABLE",editing.current(h,owner).session().status());
        var recovered=editing.start(h,owner,new SpaceEditStart("two",session.id()));
        assertNotEquals(session.id(),recovered.id());
        assertEquals("sunny_apartment",recovered.draft().presetId());
        assertEquals("RECOVERABLE",editing.cancel(h,owner,recovered.id()).status());
        homes.design(h,owner,new SpaceInput("garden_home",0,Map.of()));
        assertEquals("ENDED",editing.current(h,owner).session().status());
        assertEquals(409,assertThrows(ApiException.class,()->editing.start(h,owner,new SpaceEditStart("three",recovered.id()))).status.value());
        expireLease(session.id(),Instant.now().minusSeconds(90_000));
        assertEquals(404,assertThrows(ApiException.class,()->editing.start(h,owner,new SpaceEditStart("four",session.id()))).status.value());
        editing.purgeExpired();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM space_edit_session WHERE id=?",Integer.class,session.id()));
    }
    @Test void onlyOneEditorCanStartAndOwnershipTransferReleasesTheLease()throws Exception{
        String owner=user(),family=user(),h=homes.create(owner,new Api.CreateHouse("동시 편집","대표","sage")).household().id();
        var joined=homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->tryStart(h,owner,"a",gate));
            var second=pool.submit(()->tryStart(h,owner,"b",gate));
            gate.countDown();
            assertEquals(Set.of("ACTIVE","EDIT_BUSY"),Set.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)));
        }
        var old=editing.current(h,owner).session();
        var transfer=ownership.propose(h,owner,new ProposeOwnership("transfer",joined.meMemberId()));
        ownership.decide(h,family,transfer.id(),new DecideOwnership(transfer.version(),true),true);
        assertEquals(403,assertThrows(ApiException.class,()->editing.heartbeat(h,owner,old.id())).status.value());
        assertEquals("ACTIVE",editing.start(h,family,new SpaceEditStart("new-owner",null)).status());
        assertEquals(404,assertThrows(ApiException.class,()->editing.cancel(h,family,old.id())).status.value());
    }
    private String tryStart(String h,String owner,String key,CountDownLatch gate)throws InterruptedException{
        gate.await();
        try{return editing.start(h,owner,new SpaceEditStart(key,null)).status();}
        catch(ApiException e){return e.code;}
    }
    private void expireLease(String id,Instant time){
        jdbc.update("UPDATE space_edit_session SET lease_until=? WHERE id=?",statement->{
            statement.setTimestamp(1,Timestamp.from(time),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            statement.setString(2,id);
        });
    }
    @Test void legacyCoordinatesAndAppearanceAreNotOverwritten(){
        String owner=user(),h=homes.create(owner,new Api.CreateHouse("기존 집","대표","v1:3212")).household().id();
        jdbc.update("UPDATE membership SET avatar_space='legacy',x=320,y=288 WHERE household_id=?",h);
        var m=homes.world(h,owner).members().getFirst();assertEquals(224,m.x());assertEquals(260,m.y());assertEquals("v1:3212",m.appearance());
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
        for(var preset:List.of("warm-wood-home","sunny_apartment","brick_loft","garden_home","classic_home")){
            for(var spawn:Spaces.map(preset).get("spawns"))assertTrue(Spaces.walkable(spawn.get(0).asDouble(),spawn.get(1).asDouble(),preset));assertFalse(Spaces.walkable(-1,100,preset));assertFalse(Spaces.walkable(Double.NaN,100,preset));
            for(var object:Spaces.map(preset).get("objects"))if(object.get("interactive").asBoolean()){
                var anchor=object.get("interactionAnchor");assertTrue(Spaces.walkable(anchor.get(0).asDouble(),anchor.get(1).asDouble(),preset),preset+"/"+object.get("id"));
            }
        }
    }
}
