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
    @Autowired SpaceEditAccess editAccess;
    @Autowired OwnershipService ownership;
    @Autowired MembershipLifecycle membership;
    @Autowired MovementService movement;
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
        assertNull(editing.current(h,family).session());
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
        assertEquals(410,assertThrows(ApiException.class,()->editing.heartbeat(h,owner,old.id())).status.value());
        assertEquals("ACTIVE",editing.start(h,family,new SpaceEditStart("new-owner",null)).status());
        assertEquals(404,assertThrows(ApiException.class,()->editing.cancel(h,family,old.id())).status.value());
    }
    @Test void oneTimeApprovalBindsMemberHouseAndScopeAndSurvivesABusyEditor(){
        String owner=user(),family=user(),other=user();
        String h=homes.create(owner,new Api.CreateHouse("승인 집","대표","sage")).household().id();
        var joined=homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        homes.accept(homes.invite(h,owner).token(),other,new Api.Accept("다른 가족","sage"));
        assertEquals(403,assertThrows(ApiException.class,()->editing.start(h,family,new SpaceEditStart("direct",null))).status.value());
        var request=editAccess.ask(h,family,new AskSpaceEdit("request",Set.of(SpaceEditScope.VARIANTS)));
        assertEquals(request,editAccess.ask(h,family,new AskSpaceEdit("duplicate",Set.of(SpaceEditScope.PRESET))));
        assertEquals(joined.meMemberId(),editAccess.inbox(h,owner).requests().getFirst().memberId());
        assertTrue(editAccess.inbox(h,other).requests().isEmpty());
        var decision=new DecideSpaceEdit("approve",request.version());
        assertEquals(403,assertThrows(ApiException.class,()->editAccess.decide(h,other,request.id(),decision,true)).status.value());
        var approved=editAccess.decide(h,owner,request.id(),decision,true);
        assertEquals(approved,editAccess.decide(h,owner,request.id(),decision,true));
        assertEquals(403,assertThrows(ApiException.class,()->editing.start(h,other,new SpaceEditStart("stolen",null,approved.id()))).status.value());
        var ownerSession=editing.start(h,owner,new SpaceEditStart("owner",null));
        var start=new SpaceEditStart("member",null,approved.id());
        assertEquals("EDIT_BUSY",assertThrows(ApiException.class,()->editing.start(h,family,start)).code);
        editing.cancel(h,owner,ownerSession.id());
        var session=editing.start(h,family,start);
        assertEquals(session,editing.start(h,family,start));
        assertEquals(session.id(),editAccess.inbox(h,owner).activeEditor().sessionId());
        assertEquals(404,assertThrows(ApiException.class,()->editing.heartbeat(h,owner,session.id())).status.value());
        assertEquals("EDIT_SCOPE",assertThrows(ApiException.class,()->editing.draft(h,family,session.id(),
            new SpaceEditDraft(0L,new SpaceInput("brick_loft",0,Map.of())))).code);
        var unauthorizedLayout=new SpaceInput("garden_home",0,Map.of(),Map.of("plant",new FurniturePosition(189,96)));
        assertEquals("EDIT_SCOPE",assertThrows(ApiException.class,()->editing.draft(h,family,session.id(),
            new SpaceEditDraft(0L,unauthorizedLayout))).code);
        var draft=editing.draft(h,family,session.id(),new SpaceEditDraft(0L,new SpaceInput("garden_home",0,Map.of("fridge","leaf"))));
        var commit=new SpaceEditCommit("save",0L,draft.draftVersion());
        assertEquals(1,editing.commit(h,family,session.id(),commit).committedVersion());
        assertEquals(1,editing.commit(h,family,session.id(),commit).committedVersion());
        assertEquals(SpaceEditRequestState.USED,editAccess.inbox(h,family).requests().getFirst().state());
        assertEquals(403,assertThrows(ApiException.class,()->editing.start(h,family,new SpaceEditStart("second",null,approved.id()))).status.value());
        assertEquals("leaf",homes.design(h,other).variants().get("fridge"));
    }
    @Test void editApprovalExpiryRevocationAndDepartureNeverPublishDraft(){
        String owner=user(),family=user(),h=homes.create(owner,new Api.CreateHouse("회수 집","대표","sage")).household().id();
        homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        var pending=editAccess.ask(h,family,new AskSpaceEdit("pending",Set.of(SpaceEditScope.PRESET)));
        expireRequest(pending.id(),"expires_at");
        assertEquals(410,assertThrows(ApiException.class,()->editAccess.decide(h,owner,pending.id(),
            new DecideSpaceEdit("expired",pending.version()),true)).status.value());
        var first=grant(h,owner,family,"first");
        expireRequest(first.id(),"start_before");
        assertEquals(410,assertThrows(ApiException.class,()->editing.start(h,family,new SpaceEditStart("late",null,first.id()))).status.value());
        var next=grant(h,owner,family,"next");
        var session=editing.start(h,family,new SpaceEditStart("edit",null,next.id()));
        editing.draft(h,family,session.id(),new SpaceEditDraft(0L,new SpaceInput("brick_loft",0,Map.of())));
        expireRequest(next.id(),"start_before");
        assertEquals("ACTIVE",editing.heartbeat(h,family,session.id()).status());
        assertEquals(403,assertThrows(ApiException.class,()->editing.revoke(h,family,session.id())).status.value());
        editing.revoke(h,owner,session.id());
        assertEquals(410,assertThrows(ApiException.class,()->editing.commit(h,family,session.id(),
            new SpaceEditCommit("revoked",0L,1L))).status.value());
        assertEquals(0,homes.design(h,owner).version());
        var again=grant(h,owner,family,"again");
        var recovered=editing.start(h,family,new SpaceEditStart("recover",session.id(),again.id()));
        assertEquals("brick_loft",recovered.draft().presetId());
        membership.leave(h,family,new LeaveHouse("leave"));
        assertEquals(403,assertThrows(ApiException.class,()->editing.heartbeat(h,family,recovered.id())).status.value());
        assertNull(editAccess.inbox(h,owner).activeEditor());
        assertTrue(editAccess.inbox(h,owner).requests().isEmpty());
    }
    private SpaceRequestView grant(String h,String owner,String member,String key){
        var request=editAccess.ask(h,member,new AskSpaceEdit(key,Set.of(SpaceEditScope.PRESET)));
        return editAccess.decide(h,owner,request.id(),new DecideSpaceEdit(key+"-decision",request.version()),true);
    }
    private void expireRequest(String id,String column){
        var time=Instant.now().minusSeconds(2);
        jdbc.update("UPDATE space_edit_request SET "+column+"=? WHERE id=?",statement->{
            statement.setTimestamp(1,Timestamp.from(time),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            statement.setString(2,id);
        });
    }
    @Test void layoutUsesSameGeometryForFamilyMovementVisitorsAndLegacySettings(){
        String owner=user(),family=user(),visitor=user();
        String h=homes.create(owner,new Api.CreateHouse("배치 집","대표","sage")).household().id();
        var joined=homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        var ask=editAccess.ask(h,family,new AskSpaceEdit("move-furniture",Set.of(SpaceEditScope.LAYOUT)));
        editAccess.decide(h,owner,ask.id(),new DecideSpaceEdit("permit",ask.version()),true);
        var session=editing.start(h,family,new SpaceEditStart("layout",null,ask.id()));
        for(var invalid:List.of(Map.of("entrance",new FurniturePosition(78,50)),
            Map.of("plant",new FurniturePosition(190,96)),Map.of("sideboard",new FurniturePosition(214,236)))){
            assertEquals(400,assertThrows(ApiException.class,()->editing.draft(h,family,session.id(),
                new SpaceEditDraft(0L,new SpaceInput("garden_home",0,Map.of(),invalid)))).status.value());
        }
        var positions=Map.of("plant",new FurniturePosition(189,96));
        var changed=new SpaceInput("garden_home",0,Map.of(),positions);
        assertEquals(400,assertThrows(ApiException.class,()->SpaceLayout.validate(
            new SpaceView("garden_home",0,Map.of(),Map.of("plant",new FurniturePosition(157,112))))).status.value());
        var draft=editing.draft(h,family,session.id(),new SpaceEditDraft(0L,changed));
        assertTrue(homes.design(h,owner).positions().isEmpty());
        jdbc.update("UPDATE membership SET x=210,y=130,avatar_space='v62' WHERE id=?",joined.meMemberId());
        var saved=editing.commit(h,family,session.id(),new SpaceEditCommit("layout-save",0L,draft.draftVersion())).world();
        assertEquals(positions,saved.design().positions());
        assertEquals(224,saved.members().stream().filter(m->m.id().equals(joined.meMemberId())).findFirst().orElseThrow().x());
        assertEquals("이곳으로 이동할 수 없어요.",assertThrows(ApiException.class,()->movement.apply(h,family,
            new Api.Intent("blocked-after-move","MOVE_INTENT","home",210d,130d,null))).getMessage());
        var visit=visits.create(h,owner).url().substring(7);
        assertEquals(positions,visits.enter(visit,visitor).design().positions());
        var legacy=homes.design(h,owner,new SpaceInput("garden_home",1,Map.of("fridge","leaf")));
        assertEquals(positions,legacy.design().positions());
        assertTrue(homes.design(h,owner,new SpaceInput("sunny_apartment",2,Map.of())).design().positions().isEmpty());
    }
    @Test void pathValidationRejectsAnAccessibleTileInADisconnectedRoom(){
        var target=new SpaceLayout.Point(96,24);
        var room=new SpaceLayout.Room(new SpaceLayout.Box(0,0,112,80),
            List.of(new SpaceLayout.Item("barrier",new SpaceLayout.Point(48,0),new SpaceLayout.Box(48,0,16,80),null),
                new SpaceLayout.Item("anchor",target,null,target)),List.of(new SpaceLayout.Point(20,24)),List.of());
        assertTrue(room.walkable(target.x(),target.y()));
        assertThrows(ApiException.class,room::checkPaths);
    }
    @Test void surfacesAreValidatedScopedAndPreservedByLegacyPresetChanges(){
        String owner=user(),family=user(),guest=user();
        String h=homes.create(owner,new Api.CreateHouse("마감 집","대표","sage")).household().id();
        homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        assertEquals(SpaceSurfaces.DEFAULT,homes.design(h,family).surfaces());
        var pending=editAccess.ask(h,family,new AskSpaceEdit("surfaces",Set.of(SpaceEditScope.SURFACES)));
        var grant=editAccess.decide(h,owner,pending.id(),new DecideSpaceEdit("approve-surfaces",pending.version()),true);
        var session=editing.start(h,family,new SpaceEditStart("choose",null,grant.id()));
        var wrong=new SpaceInput("garden_home",0,Map.of(),null,new SpaceSurfaces("sage","light_oak"));
        assertEquals(400,assertThrows(ApiException.class,()->editing.draft(h,family,session.id(),new SpaceEditDraft(0L,wrong))).status.value());
        var surfaces=new SpaceSurfaces("light_oak","sage");
        var wrongScope=new SpaceInput("brick_loft",0,Map.of(),null,surfaces);
        assertEquals("EDIT_SCOPE",assertThrows(ApiException.class,()->editing.draft(h,family,session.id(),new SpaceEditDraft(0L,wrongScope))).code);
        var valid=new SpaceInput("garden_home",0,Map.of(),null,surfaces);
        var draft=editing.draft(h,family,session.id(),new SpaceEditDraft(0L,valid));
        assertEquals(SpaceSurfaces.DEFAULT,homes.design(h,owner).surfaces());
        editing.commit(h,family,session.id(),new SpaceEditCommit("apply",0L,draft.draftVersion()));
        assertEquals(surfaces,homes.design(h,owner).surfaces());
        var visit=visits.create(h,owner).url().substring(7);
        assertEquals(surfaces,visits.enter(visit,guest).design().surfaces());
        assertEquals(surfaces,homes.design(h,owner,new SpaceInput("sunny_apartment",1,Map.of())).design().surfaces());
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
