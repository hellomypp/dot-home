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
    @Autowired HomeService homes; @Autowired IdentityService identity; @Autowired Invitations invitations; @Autowired Members members; @Autowired MovementService movement; @Autowired Environment env; @Autowired ObjectMapper json;
    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"검증 구성원").id;}
    Api.World house(String user,int count){return homes.create(user,new Api.CreateHouse("검증의 집",count,"가온","sage",List.of()));}
    @Test void versionedAppearanceAndMultiplePetsSurviveWorldReload(){
        String owner=user();
        var created=homes.create(owner,new Api.CreateHouse("꾸민 집",3,"하늘","v1:3212",List.of(new Api.PetInput("모카","cat","cream"),new Api.PetInput("보리","dog","cream"))));
        var restored=homes.world(created.household().id(),owner);
        assertEquals("v1:3212",restored.members().getFirst().appearance());
        assertEquals("하늘",restored.members().getFirst().name());
        assertEquals(1,restored.members().size());
        assertEquals(Set.of("모카","보리"),restored.pets().stream().map(Api.PetView::name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("cat","dog"),restored.pets().stream().map(Api.PetView::kind).collect(java.util.stream.Collectors.toSet()));
        String legacy=user();assertEquals("sage",homes.world(house(legacy,1).household().id(),legacy).members().getFirst().appearance());
    }
    @Test void zeroPetsAndOnlyAcceptedMembersAppear(){String owner=user(),guest=user();var house=house(owner,4);assertEquals(1,house.members().size());assertTrue(house.pets().isEmpty());var invite=homes.invite(house.household().id(),owner);homes.preview(invite.token());assertEquals(1,homes.world(house.household().id(),owner).members().size());var joined=homes.accept(invite.token(),guest,new Api.Accept("나래","blue"));assertEquals(2,joined.members().size());assertEquals(2,homes.accept(invite.token(),guest,new Api.Accept("나래","blue")).members().size());assertThrows(ApiException.class,()->homes.accept(invite.token(),user(),new Api.Accept("누군가","rose")));}
    @Test void householdScopeAndOwnerAreEnforced(){String owner=user(),outsider=user();var house=house(owner,2);String id=house.household().id();assertEquals(403,assertThrows(ApiException.class,()->homes.world(id,outsider)).status.value());assertThrows(ApiException.class,()->homes.addPantry(id,outsider,new Api.CreatePantry("우유","냉장",1,"개",null)));var invitation=homes.invite(id,owner);homes.accept(invitation.token(),outsider,new Api.Accept("회원","sage"));assertThrows(ApiException.class,()->homes.invite(id,outsider));assertThrows(ApiException.class,()->homes.plan(id,owner,new Api.Plan(1)));}
    @Test void cancelledAndExpiredInvitationsDoNotJoin(){String owner=user(),guest=user();String id=house(owner,2).household().id();var invite=homes.invite(id,owner);homes.cancel(id,owner,invite.id());assertThrows(ApiException.class,()->homes.accept(invite.token(),guest,new Api.Accept("회원","blue")));var expired=homes.invite(id,owner);var row=invitations.findById(expired.id()).orElseThrow();row.expiresAt=Instant.now().minusSeconds(1);invitations.saveAndFlush(row);assertThrows(ApiException.class,()->homes.preview(expired.token()));assertEquals(1,homes.world(id,owner).members().size());}
    @Test void simultaneousAcceptanceUsesOneSlot()throws Exception{String owner=user(),a=user(),b=user();String id=house(owner,2).household().id();var first=homes.invite(id,owner);var second=homes.invite(id,owner);try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var one=pool.submit(()->{start.await();try{homes.accept(first.token(),a,new Api.Accept("A","sage"));return true;}catch(ApiException e){return false;}});var two=pool.submit(()->{start.await();try{homes.accept(second.token(),b,new Api.Accept("B","rose"));return true;}catch(ApiException e){return false;}});start.countDown();assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));}assertEquals(2,homes.world(id,owner).members().size());}
    @Test void pantryPreventsLostUpdatesAndNegativeInventory(){String owner=user();String id=house(owner,1).household().id();var item=homes.addPantry(id,owner,new Api.CreatePantry("달걀","냉장",2,"개",null));var saved=homes.updatePantry(id,owner,item.id(),new Api.UpdatePantry(1,item.version()));assertEquals(1,saved.quantity());assertTrue(saved.version()>item.version());assertThrows(ApiException.class,()->homes.updatePantry(id,owner,item.id(),new Api.UpdatePantry(0,item.version())));assertThrows(ApiException.class,()->homes.updatePantry(id,owner,item.id(),new Api.UpdatePantry(-1,saved.version())));}
    @Test void simultaneousUseOfSameInvitationIsSingleUse()throws Exception{String owner=user(),a=user(),b=user();String id=house(owner,3).household().id();var invite=homes.invite(id,owner);try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var one=pool.submit(()->{start.await();try{homes.accept(invite.token(),a,new Api.Accept("A","sage"));return true;}catch(ApiException e){return false;}});var two=pool.submit(()->{start.await();try{homes.accept(invite.token(),b,new Api.Accept("B","blue"));return true;}catch(ApiException e){return false;}});start.countDown();assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));}assertEquals(2,homes.world(id,owner).members().size());}
    @Test void eventsAreIdempotentAndAuthorComesFromAuthenticatedUser(){String owner=user(),guest=user();String id=house(owner,2).household().id();var invite=homes.invite(id,owner);homes.accept(invite.token(),guest,new Api.Accept("나래","rose"));String request=UUID.randomUUID().toString();var input=new Api.CreateEvent(request,"PARCEL_ARRIVED","택배 도착");var first=homes.event(id,guest,input);var second=homes.event(id,guest,input);assertEquals(first.id(),second.id());assertEquals("나래",first.authorName());assertEquals(1,homes.events(id,owner).size());assertThrows(ApiException.class,()->homes.event(id,guest,new Api.CreateEvent(request,"NOTE","다른 내용")));}
    @Test void expiredStatusIsNotExposedAsCurrent(){String owner=user();var house=house(owner,1);homes.status(house.household().id(),owner,new Api.Status("office","좋아요","수프","안녕",1));var member=members.findById(house.meMemberId()).orElseThrow();member.statusExpiresAt=Instant.now().minusSeconds(1);members.saveAndFlush(member);var world=homes.world(house.household().id(),owner);assertEquals("",world.members().getFirst().thought());assertEquals("office",world.members().getFirst().place());}
    @Test void movementNeverChangesInventoryAndRejectsFurniture(){String owner=user();String id=house(owner,1).household().id();homes.addPantry(id,owner,new Api.CreatePantry("우유","냉장",2,"개",null));assertThrows(ApiException.class,()->movement.apply(id,owner,new Api.Intent(UUID.randomUUID().toString(),"MOVE_INTENT","home",400d,245d,null)));assertEquals(2,homes.pantry(id,owner).getFirst().quantity());assertFalse(MovementService.walkable(Double.NaN,200,"home"));assertFalse(MovementService.walkable(900,200,"home"));}
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
                {"name":"온보딩 HTTP 집","plannedPeople":2,"memberName":"가온","appearance":"v1:3212","pets":[{"name":"모카","kind":"cat","appearance":"cream"},{"name":"보리","kind":"dog","appearance":"cream"}]}
                """;
            var created=a.send(HttpRequest.newBuilder(URI.create(base+"/api/households")).header("Content-Type","application/json").header("X-CSRF-TOKEN",authenticatedToken).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,created.statusCode(),created.body());
            var world=json.readTree(created.body());String householdId=world.get("household").get("id").asText();
            assertEquals("v1:3212",world.get("members").get(0).get("appearance").asText());assertEquals(2,world.get("pets").size());
            var restored=a.send(HttpRequest.newBuilder(URI.create(base+"/api/households/"+householdId+"/world")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,restored.statusCode());assertEquals("v1:3212",json.readTree(restored.body()).get("members").get(0).get("appearance").asText());
            assertEquals(401,b.send(HttpRequest.newBuilder(URI.create(base+"/api/households/"+householdId+"/world")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());

        }
    }
}
