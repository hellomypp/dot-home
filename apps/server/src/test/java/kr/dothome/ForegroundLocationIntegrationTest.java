package kr.dothome;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class ForegroundLocationIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired ForegroundLocation location;
    @Autowired LocationDevices devices;
    @Autowired ManualArrival manualArrival;
    @Autowired NotificationPreferenceService preferences;
    @Autowired HouseholdNotificationService notifications;
    @Autowired ObjectMapper json;

    record Family(String owner,String member,String first,String second) {}
    Family family(){
        var owner=user();var member=user();
        var first=house(owner,member);var second=house(owner,member);
        return new Family(owner,member,first,second);
    }
    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"검증 가족").id;}
    String house(String owner,String member){
        var h=homes.create(owner,new Api.CreateHouse("귀가 검증","대표","sage")).household().id();
        homes.accept(homes.invite(h,owner).token(),member,new Api.Accept("가족","sage"));
        return h;
    }
    void consent(String h,String user,boolean enabled,long version){
        var change=json.readTree("{\"enabled\":"+enabled+",\"version\":"+version+"}");
        homes.expressions(h,user,new ExpressionPatch(UUID.randomUUID().toString(),Map.of("sharing",change)));
    }
    LocationDeviceView register(String user,UUID device,boolean replace,long generation){
        return location.register(user,new RegisterLocationDevice(UUID.randomUUID(),device,replace,generation));
    }
    LocationSharingView share(String h,String user,UUID device,UUID config){
        var view=location.sharing(h,user);
        return location.share(h,user,new ChangeLocationSharing(true,view.generation(),view.version(),device,config));
    }
    LocationObservation observation(UUID device,UUID config,LocationSharingView settings,long seq,Instant time,PhysicalState state){
        return new LocationObservation(UUID.randomUUID(),device,settings.generation(),settings.version(),seq,time,state,0,20.0,config);
    }
    SharedPlace visible(String h,Family family){
        return homes.world(h,family.member()).members().stream().filter(m->m.userId().equals(family.owner()))
            .findFirst().orElseThrow().sharedPlace();
    }

    @Test void separateConsentSwitchAndReplacementInvalidateOldObservations(){
        var f=family();var phone=UUID.randomUUID();var tablet=UUID.randomUUID();var config=UUID.randomUUID();
        assertEquals(0,location.device(f.owner(),phone).generation());
        assertFalse(location.sharing(f.first(),f.owner()).enabled());
        assertEquals(403,assertThrows(ApiException.class,()->location.sharing(f.first(),user())).status.value());
        assertEquals(1,register(f.owner(),phone,false,0).generation());
        assertEquals(1,register(f.owner(),phone,false,0).generation());
        assertThrows(ApiException.class,()->share(f.first(),f.owner(),phone,config));
        consent(f.first(),f.owner(),true,0);
        consent(f.second(),f.owner(),true,0);
        var first=share(f.first(),f.owner(),phone,config);
        var old=observation(phone,config,first,1,Instant.now(),PhysicalState.HOME);
        location.observe(f.first(),f.owner(),old);
        assertEquals("AUTO",visible(f.first(),f).status());
        assertEquals("UNKNOWN",visible(f.second(),f).status());
        assertThrows(ApiException.class,()->location.observe(f.second(),f.owner(),old));

        var second=share(f.second(),f.owner(),phone,config);
        assertEquals(f.second(),location.device(f.owner(),phone).activeHouseId());
        assertEquals("UNKNOWN",visible(f.first(),f).status());
        assertThrows(ApiException.class,()->location.observe(f.first(),f.owner(),old));
        var outside=observation(phone,config,second,1,Instant.now(),PhysicalState.OUTSIDE);
        location.observe(f.second(),f.owner(),outside);
        assertEquals("OUTSIDE",visible(f.second(),f).place());
        assertThrows(ApiException.class,()->register(f.owner(),tablet,false,1));
        assertThrows(ApiException.class,()->register(f.owner(),tablet,true,0));
        assertEquals(2,register(f.owner(),tablet,true,1).generation());
        assertNull(location.device(f.owner(),phone).activeHouseId());
        assertFalse(location.device(f.owner(),phone).currentDevice());
        assertEquals("UNKNOWN",visible(f.second(),f).status());
        assertThrows(ApiException.class,()->location.observe(f.second(),f.owner(),outside));
        assertFalse(location.sharing(f.second(),f.owner()).enabled());
    }

    @Test void firstFixAndExpiredOrInaccurateDataNeverCreateArrivalAndRetryIsSafe(){
        var f=family();var phone=UUID.randomUUID();var config=UUID.randomUUID();
        register(f.owner(),phone,false,0);
        consent(f.first(),f.owner(),true,0);
        var settings=share(f.first(),f.owner(),phone,config);
        var h=f.first();var user=f.owner();var now=Instant.now().minusSeconds(10);
        var first=observation(phone,config,settings,1,now,PhysicalState.HOME);
        location.observe(h,user,first);
        location.observe(h,user,first);
        assertTrue(homes.events(h,user).isEmpty());
        var inaccurate=new LocationObservation(UUID.randomUUID(),phone,settings.generation(),settings.version(),
            2L,now.plusSeconds(1),PhysicalState.OUTSIDE,0,101.0,config);
        assertEquals(400,assertThrows(ApiException.class,()->location.observe(h,user,inaccurate)).status.value());
        assertThrows(ApiException.class,()->location.observe(h,user,observation(phone,config,settings,2,
            now.minusSeconds(200),PhysicalState.OUTSIDE)));
        assertThrows(ApiException.class,()->location.observe(h,user,observation(phone,config,settings,2,
            now.plusSeconds(100),PhysicalState.OUTSIDE)));
        location.observe(h,user,observation(phone,config,settings,2,now.plusSeconds(1),PhysicalState.OUTSIDE));
        assertThrows(ApiException.class,()->location.observe(h,user,observation(phone,config,settings,1,
            now.plusSeconds(2),PhysicalState.HOME)));
        location.observe(h,user,observation(phone,config,settings,3,now.plusSeconds(2),PhysicalState.UNKNOWN));
        assertEquals("UNKNOWN",visible(h,f).status());
        assertTrue(homes.events(h,user).isEmpty());
        var arrival=observation(phone,config,settings,4,now.plusSeconds(3),PhysicalState.HOME);
        location.observe(h,user,arrival);
        location.observe(h,user,arrival);
        location.observe(h,user,observation(phone,config,settings,5,now.plusSeconds(4),PhysicalState.OUTSIDE));
        location.observe(h,user,observation(phone,config,settings,6,now.plusSeconds(5),PhysicalState.HOME));
        var events=homes.events(h,f.member());
        assertEquals(1,events.size());
        assertEquals("ARRIVED_HOME",events.getFirst().type());
        assertEquals("AUTO",events.getFirst().source());
        assertTrue(homes.events(f.second(),user).isEmpty());
        var row=devices.findById(user).orElseThrow();
        row.receivedAt=Instant.now().minusSeconds(301);
        devices.saveAndFlush(row);
        assertEquals("UNKNOWN",visible(h,f).status());
        consent(h,user,false,1);
        consent(h,user,true,2);
        assertEquals("UNKNOWN",visible(h,f).status());
        assertThrows(ApiException.class,()->location.observe(h,user,observation(phone,config,settings,7,
            now.plusSeconds(6),PhysicalState.HOME)));
    }

    @Test void manualArrivalIsReplayableAndNotifiesOnlyOptedInFamily() {
        var f=family();var h=f.first();var user=f.owner();
        assertFalse(preferences.get(h,f.member()).enabled().get(NotificationGroup.ARRIVAL));
        preferences.patch(h,f.member(),new ChangeNotificationSettings(Map.of(NotificationGroup.ARRIVAL,true),0L));
        var privateRecord=manualArrival.record(h,user,new RecordArrival(UUID.randomUUID(),"귀가 기록만"));
        assertFalse(privateRecord.alreadyHome());
        assertEquals("MANUAL",privateRecord.event().source());
        assertEquals("UNSHARED",visible(h,f).status());
        assertTrue(notifications.list(h,f.member(),null,10).items().isEmpty());
        assertThrows(ApiException.class,()->manualArrival.record(h,user(),new RecordArrival(UUID.randomUUID(),"권한 없음")));

        consent(h,user,true,0);
        var original=new RecordArrival(UUID.randomUUID(),"다녀왔어요");
        var recorded=manualArrival.record(h,user,original);
        assertNotNull(recorded.event());
        assertEquals(recorded,manualArrival.record(h,user,original));
        assertThrows(ApiException.class,()->manualArrival.record(h,user,new RecordArrival(original.requestId(),"다른 내용")));
        var ignored=new RecordArrival(UUID.randomUUID(),"다시 도착");
        assertTrue(manualArrival.record(h,user,ignored).alreadyHome());
        assertTrue(manualArrival.record(h,user,ignored).alreadyHome());
        assertEquals("MANUAL",visible(h,f).status());
        assertEquals("HOME",visible(h,f).place());
        assertEquals(120,java.time.Duration.between(visible(h,f).sharedAt(),visible(h,f).expiresAt()).toMinutes());
        assertEquals(2,homes.events(h,user).size());
        assertEquals(NotificationKind.ARRIVED_HOME,notifications.list(h,f.member(),null,10).items().getFirst().kind());
        assertEquals(1,notifications.list(h,f.member(),null,10).unreadCount());
        assertTrue(notifications.list(f.second(),f.member(),null,10).items().isEmpty());
        assertTrue(notifications.list(h,user,null,10).items().isEmpty());
    }

    @Test void automaticAndManualArrivalRacingProduceOneEventAndOneAlert() throws Exception {
        var f=family();var h=f.first();var user=f.owner();var phone=UUID.randomUUID();var config=UUID.randomUUID();
        register(user,phone,false,0);
        consent(h,user,true,0);
        var settings=share(h,user,phone,config);
        preferences.patch(h,f.member(),new ChangeNotificationSettings(Map.of(NotificationGroup.ARRIVAL,true),0L));
        location.observe(h,user,observation(phone,config,settings,1,Instant.now().minusSeconds(5),PhysicalState.OUTSIDE));
        var start=new CountDownLatch(1);
        try(var threads=Executors.newVirtualThreadPerTaskExecutor()){
            var a=threads.submit(()->{start.await();location.observe(h,user,observation(phone,config,settings,2,Instant.now(),PhysicalState.HOME));return null;});
            var b=threads.submit(()->{start.await();return manualArrival.record(h,user,new RecordArrival(UUID.randomUUID(),"다녀왔어요"));});
            start.countDown();
            a.get(10,TimeUnit.SECONDS);
            b.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,homes.events(h,user).size());
        assertEquals(1,notifications.list(h,f.member(),null,10).unreadCount());
    }

    @Test void simultaneousInitialRegistrationHasOneProvider() throws Exception {
        String user=user();
        var first=UUID.randomUUID();var second=UUID.randomUUID();
        var start=new CountDownLatch(1);
        try(var threads=Executors.newVirtualThreadPerTaskExecutor()){
            var a=threads.submit(()->{start.await();return tryRegister(user,first);});
            var b=threads.submit(()->{start.await();return tryRegister(user,second);});
            start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertNotEquals(location.device(user,first).currentDevice(),location.device(user,second).currentDevice());
    }

    private boolean tryRegister(String user,UUID device){
        try{register(user,device,false,0);return true;}
        catch(ApiException ex){assertEquals("LOCATION_DEVICE_CHANGED",ex.code);return false;}
    }
}
