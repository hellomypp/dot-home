package kr.dothome;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev","test"})
class HouseholdIntegrationTest {
    @Autowired HomeService homes; @Autowired IdentityService identity; @Autowired Invitations invitations; @Autowired Members members; @Autowired MemberExpressions expressions; @Autowired MovementService movement; @Autowired Environment env; @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired MembershipLifecycle membership;
    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"검증 구성원").id;}
    @Test void nicknameIsOwnHouseholdProfileAndSurvivesRelogin(){
        String key=UUID.randomUUID().toString(),owner=user();
        String guest=identity.resolve("google",key,"Google 이름").id;
        String h=house(owner).household().id();
        var before=homes.accept(homes.invite(h,owner).token(),guest,new Api.Accept("이전 이름","v1:3212"));
        var renamed=homes.profile(h,guest,new Api.Profile("  새 닉네임  "));
        assertEquals(before.meMemberId(),renamed.meMemberId());
        var me=renamed.members().stream().filter(m->m.id().equals(renamed.meMemberId())).findFirst().orElseThrow();
        assertEquals("새 닉네임",me.name());assertEquals("v1:3212",me.appearance());
        assertEquals("가온",renamed.members().stream().filter(m->m.userId().equals(owner)).findFirst().orElseThrow().name());
        assertTrue(renamed.revision()>before.revision());
        assertEquals(guest,identity.resolve("google",key,"다른 Google 이름").id);
        assertTrue(homes.world(h,guest).members().stream().anyMatch(m->m.id().equals(me.id())&&m.name().equals("새 닉네임")));
        assertEquals(2,members.countByHouseholdIdAndActiveTrue(h));
        assertThrows(ApiException.class,()->homes.profile(h,user(),new Api.Profile("위조")));
        for(String bad:List.of(" ","x".repeat(41),"이름\n줄바꿈","제어\u0001문자"))assertThrows(ApiException.class,()->homes.profile(h,guest,new Api.Profile(bad)));
        assertEquals("새 닉네임",members.findById(me.id()).orElseThrow().name);
    }
    Api.World house(String user){return homes.create(user,new Api.CreateHouse("검증의 집","가온","sage"));}
    @Test void versionedAppearanceAndMultiplePetsSurviveWorldReload(){
        String owner=user();
        var created=homes.create(owner,new Api.CreateHouse("꾸민 집","하늘","v1:3212"));
        assertTrue(created.pets().isEmpty());
        homes.addPet(created.household().id(),owner,new Api.PetInput("모카","cat","cream"));
        homes.addPet(created.household().id(),owner,new Api.PetInput("보리","dog","cream"));
        var restored=homes.world(created.household().id(),owner);
        assertEquals("v1:3212",restored.members().getFirst().appearance());
        assertEquals("하늘",restored.members().getFirst().name());
        assertEquals(1,restored.members().size());
        assertEquals(Set.of("모카","보리"),restored.pets().stream().map(Api.PetView::name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("cat","dog"),restored.pets().stream().map(Api.PetView::kind).collect(java.util.stream.Collectors.toSet()));
        String legacy=user();assertEquals("sage",homes.world(house(legacy).household().id(),legacy).members().getFirst().appearance());
    }
    @Test void legacyLimitDoesNotRestrictAdmissionAndPetsStayHouseScoped(){
        String owner=user(),family=user(),outsider=user(),h=house(owner).household().id();
        jdbc.update("UPDATE household SET planned_people=1 WHERE id=?",h);
        homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","blue"));
        assertEquals(2,homes.world(h,family).members().size());
        var created=homes.addPet(h,owner,new Api.PetInput(" 모카 ","cat","cream"));
        var pet=created.pets().getFirst();assertEquals("모카",pet.name());
        var changed=homes.editPet(h,owner,pet.id(),new Api.PetEdit("보리","dog"));
        assertEquals("보리",changed.pets().getFirst().name());assertEquals("dog",changed.pets().getFirst().kind());
        assertEquals("cream",changed.pets().getFirst().appearance());
        assertEquals("보리",homes.world(h,family).pets().getFirst().name());
        assertThrows(ApiException.class,()->homes.editPet(h,family,pet.id(),new Api.PetEdit("금지","cat")));
        assertThrows(ApiException.class,()->homes.editPet(h,outsider,pet.id(),new Api.PetEdit("금지","cat")));
        String other=house(owner).household().id();
        assertThrows(ApiException.class,()->homes.editPet(other,owner,pet.id(),new Api.PetEdit("위조","cat")));
        for(String name:List.of(" ","x".repeat(41),"잘못된\n이름"))assertThrows(ApiException.class,()->homes.addPet(h,owner,new Api.PetInput(name,"cat","cream")));
        assertThrows(ApiException.class,()->homes.addPet(h,owner,new Api.PetInput("이름","other","cream")));
        assertThrows(ApiException.class,()->homes.addPet(h,owner,new Api.PetInput("이름","cat","invalid")));
        assertEquals(1,homes.world(h,owner).pets().size());
    }
    @Test void petSpeciesAndGuardianAreScopedAndVersioned(){
        String owner=user(),family=user(),outsider=user(),h=house(owner).household().id();
        var membership=homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        String guardian=membership.meMemberId(), outsiderMember=house(outsider).meMemberId();
        assertThrows(ApiException.class,()->homes.addPet(h,family,new Api.PetInput("별이",null,null,PetSpecies.FISH,"베타",outsiderMember)));
        var fish=homes.addPet(h,family,new Api.PetInput("별이",null,null,PetSpecies.FISH," 베타 ",null)).pets().getFirst();
        assertEquals("TANK",fish.habitat());assertEquals("tank",fish.appearanceId());assertEquals("other",fish.kind());
        assertEquals("베타",fish.speciesName());assertEquals(guardian,fish.guardianMemberId());
        assertEquals(2,homes.world(h,owner).members().size());
        assertThrows(ApiException.class,()->homes.editPet(h,outsider,fish.id(),new Api.PetEdit("위조",null,PetSpecies.FISH,null,null,0L)));
        var updated=homes.editPet(h,family,fish.id(),new Api.PetEdit("별밤",null,PetSpecies.FISH,null,null,0L)).pets().getFirst();
        assertEquals(1L,updated.version());assertEquals("베타",updated.speciesName());
        assertThrows(ApiException.class,()->homes.editPet(h,owner,fish.id(),new Api.PetEdit("충돌",null,PetSpecies.FISH,null,null,0L)));
        assertEquals(updated,homes.world(h,owner).pets().getFirst());
    }
    @Test void parallelFreshInvitesAfterDepartureKeepOneActiveMembership()throws Exception{
        String owner=user(),family=user(),h=house(owner).household().id();
        var old=homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("첫 가입","sage"));
        membership.leave(h,family,new LeaveHouse("leave"));
        var first=homes.invite(h,owner);
        var second=homes.invite(h,owner);
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return homes.accept(first.token(),family,new Api.Accept("재가입","sage"));});
            var b=pool.submit(()->{start.await();return homes.accept(second.token(),family,new Api.Accept("재가입","sage"));});
            start.countDown();
            String active=a.get(10,TimeUnit.SECONDS).meMemberId();
            assertEquals(active,b.get(10,TimeUnit.SECONDS).meMemberId());
            assertNotEquals(old.meMemberId(),active);
        }
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM membership WHERE household_id=? AND user_id=?",Integer.class,h,family));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM membership WHERE household_id=? AND user_id=? AND active=TRUE",Integer.class,h,family));
    }
    @Test void sameUserConcurrentDifferentInvitesNeverCreateDuplicateMembership()throws Exception{
        String owner=user(),guest=user(),h=house(owner).household().id();var a=homes.invite(h,owner);var b=homes.invite(h,owner);
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            var one=pool.submit(()->{start.await();return homes.accept(a.token(),guest,new Api.Accept("가족","sage"));});
            var two=pool.submit(()->{start.await();return homes.accept(b.token(),guest,new Api.Accept("가족","sage"));});
            start.countDown();assertEquals(one.get(10,TimeUnit.SECONDS).meMemberId(),two.get(10,TimeUnit.SECONDS).meMemberId());
        }
        assertEquals(2,homes.world(h,owner).members().size());
    }
    @Test void zeroPetsAndOnlyAcceptedMembersAppear(){String owner=user(),guest=user();var house=house(owner);assertEquals(1,house.members().size());assertTrue(house.pets().isEmpty());var invite=homes.invite(house.household().id(),owner);homes.preview(invite.token());assertEquals(1,homes.world(house.household().id(),owner).members().size());var joined=homes.accept(invite.token(),guest,new Api.Accept("나래","blue"));assertEquals(2,joined.members().size());assertEquals(2,homes.accept(invite.token(),guest,new Api.Accept("나래","blue")).members().size());assertThrows(ApiException.class,()->homes.accept(invite.token(),user(),new Api.Accept("누군가","rose")));}
    @Test void householdScopeAndOwnerAreEnforced(){String owner=user(),outsider=user();var house=house(owner);String id=house.household().id();assertEquals(403,assertThrows(ApiException.class,()->homes.world(id,outsider)).status.value());assertThrows(ApiException.class,()->homes.addPantry(id,outsider,new Api.CreatePantry("우유","냉장",1,"개",null)));var invitation=homes.invite(id,owner);homes.accept(invitation.token(),outsider,new Api.Accept("회원","sage"));assertThrows(ApiException.class,()->homes.invite(id,outsider));}
    @Test void newInvitesLastSevenDaysAndExistingExpiryIsPreserved(){
        String owner=user(),id=house(owner).household().id();
        Instant issuedAfter=Instant.now();
        var existing=homes.invite(id,owner);
        var row=invitations.findById(existing.id()).orElseThrow();
        Instant legacyExpiry=issuedAfter.plus(Duration.ofHours(72)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        row.expiresAt=legacyExpiry;
        invitations.saveAndFlush(row);

        var fresh=homes.invite(id,owner);
        assertFalse(fresh.expiresAt().isBefore(issuedAfter.plus(Duration.ofDays(7))));
        assertFalse(fresh.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(7))));
        assertEquals(legacyExpiry,homes.preview(existing.token()).expiresAt());
    }
    @Test void cancelledAndExpiredInvitationsDoNotJoin(){String owner=user(),guest=user();String id=house(owner).household().id();var invite=homes.invite(id,owner);homes.cancel(id,owner,invite.id());assertThrows(ApiException.class,()->homes.accept(invite.token(),guest,new Api.Accept("회원","blue")));var expired=homes.invite(id,owner);var row=invitations.findById(expired.id()).orElseThrow();row.expiresAt=Instant.now().minusSeconds(1);invitations.saveAndFlush(row);assertThrows(ApiException.class,()->homes.preview(expired.token()));assertEquals(1,homes.world(id,owner).members().size());}
    @Test void differentInvitationsCanBothBeAcceptedConcurrently()throws Exception{String owner=user(),a=user(),b=user();String id=house(owner).household().id();var first=homes.invite(id,owner);var second=homes.invite(id,owner);try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var one=pool.submit(()->{start.await();try{homes.accept(first.token(),a,new Api.Accept("A","sage"));return true;}catch(ApiException e){return false;}});var two=pool.submit(()->{start.await();try{homes.accept(second.token(),b,new Api.Accept("B","rose"));return true;}catch(ApiException e){return false;}});start.countDown();assertTrue(one.get(10,TimeUnit.SECONDS));assertTrue(two.get(10,TimeUnit.SECONDS));}assertEquals(3,homes.world(id,owner).members().size());}
    @Test void pantryPreventsLostUpdatesAndNegativeInventory(){String owner=user();String id=house(owner).household().id();var item=homes.addPantry(id,owner,new Api.CreatePantry("달걀","냉장",2,"개",null));var saved=homes.updatePantry(id,owner,item.id(),new Api.UpdatePantry(1,item.version()));assertEquals(1,saved.quantity());assertTrue(saved.version()>item.version());assertThrows(ApiException.class,()->homes.updatePantry(id,owner,item.id(),new Api.UpdatePantry(0,item.version())));assertThrows(ApiException.class,()->homes.updatePantry(id,owner,item.id(),new Api.UpdatePantry(-1,saved.version())));}
    @Test void simultaneousUseOfSameInvitationIsSingleUse()throws Exception{String owner=user(),a=user(),b=user();String id=house(owner).household().id();var invite=homes.invite(id,owner);try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var one=pool.submit(()->{start.await();try{homes.accept(invite.token(),a,new Api.Accept("A","sage"));return true;}catch(ApiException e){return false;}});var two=pool.submit(()->{start.await();try{homes.accept(invite.token(),b,new Api.Accept("B","blue"));return true;}catch(ApiException e){return false;}});start.countDown();assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));}assertEquals(2,homes.world(id,owner).members().size());}
    @Test void eventsAreIdempotentAndAuthorComesFromAuthenticatedUser(){String owner=user(),guest=user();String id=house(owner).household().id();var invite=homes.invite(id,owner);homes.accept(invite.token(),guest,new Api.Accept("나래","rose"));String request=UUID.randomUUID().toString();var input=new Api.CreateEvent(request,"PARCEL_ARRIVED","택배 도착");var first=homes.event(id,guest,input);var second=homes.event(id,guest,input);assertEquals(first.id(),second.id());assertEquals("나래",first.authorName());assertEquals(1,homes.events(id,owner).size());assertThrows(ApiException.class,()->homes.event(id,guest,new Api.CreateEvent(request,"NOTE","다른 내용")));}
    @Test void expiredStatusIsNotExposedAsCurrent(){String owner=user();var house=house(owner);homes.status(house.household().id(),owner,new Api.Status("office","좋아요","수프","안녕",1));var statuses=expressions.findByMemberIdIn(List.of(house.meMemberId()));statuses.forEach(status->status.expiresAt=Instant.now().minusSeconds(1));expressions.saveAllAndFlush(statuses);var world=homes.world(house.household().id(),owner);assertEquals("",world.members().getFirst().thought());assertEquals("office",world.members().getFirst().place());}
    @Test void movementNeverChangesInventoryAndRejectsFurniture(){String owner=user();String id=house(owner).household().id();homes.addPantry(id,owner,new Api.CreatePantry("우유","냉장",2,"개",null));assertThrows(ApiException.class,()->movement.apply(id,owner,new Api.Intent(UUID.randomUUID().toString(),"MOVE_INTENT","home",400d,245d,null)));assertEquals(2,homes.pantry(id,owner).getFirst().quantity());assertFalse(MovementService.walkable(Double.NaN,200,"home"));assertFalse(MovementService.walkable(900,200,"home"));}
    @Test void twoCookieSessionsAndCsrfHaveRealHttpIsolation()throws Exception{
        String base="http://127.0.0.1:"+env.getProperty("local.server.port");var cookieA=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var cookieB=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        try(var a=HttpClient.newBuilder().cookieHandler(cookieA).build();var b=HttpClient.newBuilder().cookieHandler(cookieB).build()){
            assertEquals(401,a.send(HttpRequest.newBuilder(URI.create(base+"/api/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            String login="{\"accountKey\":\"http-"+UUID.randomUUID()+"\",\"name\":\"가온\"}";
            assertEquals(403,a.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/dev")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(login)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            var csrf=a.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());String token=json.readTree(csrf.body()).get("token").asText();
            var logged=a.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/dev")).header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofString(login)).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,logged.statusCode(),logged.body());
            assertEquals(200,a.send(HttpRequest.newBuilder(URI.create(base+"/api/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(401,b.send(HttpRequest.newBuilder(URI.create(base+"/api/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            var authenticatedCsrf=a.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            String authenticatedToken=json.readTree(authenticatedCsrf.body()).get("token").asText();
            String body="""
                {"name":"온보딩 HTTP 집","memberName":"가온","appearance":"v1:3212"}
                """;
            var created=a.send(HttpRequest.newBuilder(URI.create(base+"/api/households")).header("Content-Type","application/json").header("X-CSRF-TOKEN",authenticatedToken).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,created.statusCode(),created.body());
            var world=json.readTree(created.body());String householdId=world.get("household").get("id").asText();
            assertEquals("v1:3212",world.get("members").get(0).get("appearance").asText());assertEquals(0,world.get("pets").size());assertFalse(world.get("household").has("plannedPeople"));
            var restored=a.send(HttpRequest.newBuilder(URI.create(base+"/api/households/"+householdId+"/world")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,restored.statusCode());assertEquals("v1:3212",json.readTree(restored.body()).get("members").get(0).get("appearance").asText());
            assertEquals(401,b.send(HttpRequest.newBuilder(URI.create(base+"/api/households/"+householdId+"/world")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());

        }
    }
}
