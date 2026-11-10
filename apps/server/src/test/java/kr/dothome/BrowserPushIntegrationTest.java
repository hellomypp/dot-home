package kr.dothome;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="app.push.poll-delay=PT24H") @ActiveProfiles({"dev","test"})
class BrowserPushIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired PushRegistration registration;
    @Autowired BrowserPushOutbox outbox;
    @Autowired PushDeliveries deliveries;
    @Autowired HouseholdRequestService requests;
    @Autowired HouseholdNotificationService notifications;
    @Autowired NotificationPreferenceService preferences;
    @Autowired SpaceEditAccess edits;
    @Autowired SpaceEditRequests editRows;
    @Autowired MembershipLifecycle membership;
    @MockitoBean PushTransport transport;

    record Family(String house,String owner,String family,String endpoint){}
    private String user(){return identity.resolve("test",UUID.randomUUID().toString(),"푸시 검증").id;}

    @BeforeEach void configure(){
        when(transport.enabled()).thenReturn(true);
        when(transport.publicKey()).thenReturn("public-test-key");
        when(transport.fingerprint()).thenReturn("f".repeat(64));
    }

    Family family(){
        var owner=user();var family=user();
        var h=homes.create(owner,new Api.CreateHouse("푸시 검증","대표","sage")).household().id();
        homes.accept(homes.invite(h,owner).token(),family,new Api.Accept("가족","sage"));
        preferences.patch(h,family,new ChangeNotificationSettings(null,0L,new PushQuietHours(null,null,"Asia/Seoul")));
        var endpoint="https://fcm.googleapis.com/fcm/send/"+UUID.randomUUID();
        registration.register(family,endpoint,"test-session",null);
        return new Family(h,owner,family,endpoint);
    }

    void notice(Family f,String key){
        requests.create(f.house(),f.owner(),new CreateRequest(key,RequestKind.NOTICE,"공동 공지","",List.of(),null));
    }

    String delivery(Family f){
        var noticeId=notifications.list(f.house(),f.family(),null,10).items().getFirst().id();
        return deliveries.findAll().stream().filter(d->d.notificationId.equals(noticeId)).findFirst().orElseThrow().id;
    }

    @Test void subscriptionOwnershipReadAndSettingsAreRecheckedBeforeSend(){
        var f=family();
        registration.register(f.family(),f.endpoint(),"test-session",null);
        assertTrue(registration.status(f.family(),f.endpoint(),"test-session").enabled());
        assertFalse(registration.status(f.owner(),f.endpoint(),"other-session").enabled());
        assertEquals("PUSH_SUBSCRIPTION_OWNED",assertThrows(ApiException.class,()->registration.register(f.owner(),f.endpoint(),"other-session",null)).code);
        notice(f,"first");notice(f,"first");
        String id=delivery(f);
        assertNotNull(outbox.prepare(id,Instant.now()));
        assertEquals(1,notifications.list(f.house(),f.family(),null,10).items().size());
        notifications.read(notifications.list(f.house(),f.family(),null,10).items().getFirst().id(),f.family());
        assertNull(outbox.prepare(id,Instant.now()));
        notice(f,"second");
        id=delivery(f);
        var version=preferences.get(f.house(),f.family()).version();
        preferences.patch(f.house(),f.family(),new ChangeNotificationSettings(Map.of(NotificationGroup.NOTICE,false),version));
        assertNull(outbox.prepare(id,Instant.now()));
        assertTrue(notifications.list(f.house(),f.family(),null,10).unreadCount()>0);
        registration.disable(f.owner(),f.endpoint());
        assertTrue(registration.status(f.family(),f.endpoint(),"test-session").enabled());
        registration.disable(f.family(),f.endpoint());
        assertFalse(registration.status(f.family(),f.endpoint(),"test-session").enabled());
    }

    @Test void lockScreenDetailsAreOffByDefaultAndRecheckedPerHouse() {
        var f=family();
        var keys=new PushKeyInput(
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
            "BTBZMqHH6r4Tts7J_aSIgg");
        registration.register(f.family(),f.endpoint(),"test-session",keys);
        assertTrue(registration.status(f.family(),f.endpoint(),"test-session").payloadReady());
        notice(f,"preview");
        String id=delivery(f);
        var generic=outbox.prepare(id,Instant.now());
        assertEquals(f.house(),generic.payload().householdId());
        assertNull(generic.payload().preview());
        var version=preferences.get(f.house(),f.family()).version();
        preferences.patch(f.house(),f.family(),new ChangeNotificationSettings(null,version,null,true));
        assertEquals("대표 · 가족 공지",outbox.prepare(id,Instant.now()).payload().preview());
        preferences.patch(f.house(),f.family(),new ChangeNotificationSettings(null,version+1,null,false));
        assertNull(outbox.prepare(id,Instant.now()).payload().preview());
    }

    @Test void quietTimeDoesNotQueueOrDelayOldAlertsAndMembershipRevokesPendingSend(){
        var f=family();
        int minute=Instant.now().atZone(ZoneId.of("Asia/Seoul")).getHour()*60+Instant.now().atZone(ZoneId.of("Asia/Seoul")).getMinute();
        var version=preferences.get(f.house(),f.family()).version();
        preferences.patch(f.house(),f.family(),new ChangeNotificationSettings(null,version,
            new PushQuietHours((minute+1439)%1440,(minute+2)%1440,"Asia/Seoul")));
        notice(f,"quiet");
        var quietId=notifications.list(f.house(),f.family(),null,10).items().getFirst().id();
        assertTrue(deliveries.findAll().stream().noneMatch(d->d.notificationId.equals(quietId)));
        version=preferences.get(f.house(),f.family()).version();
        preferences.patch(f.house(),f.family(),new ChangeNotificationSettings(null,version,new PushQuietHours(null,null,"Asia/Seoul")));
        notice(f,"active");
        String id=delivery(f);
        membership.leave(f.house(),f.family(),new LeaveHouse("leave"));
        assertNull(outbox.prepare(id,Instant.now()));
    }

    @Test void expiredApprovalIsNeverSentAndTransientFailureKeepsSameDelivery(){
        var f=family();
        registration.register(f.owner(),"https://web.push.apple.com/"+UUID.randomUUID(),"owner-session",null);
        preferences.patch(f.house(),f.owner(),new ChangeNotificationSettings(null,0L,new PushQuietHours(null,null,"Asia/Seoul")));
        var requested=edits.ask(f.house(),f.family(),new AskSpaceEdit("ask",Set.of(SpaceEditScope.PRESET)));
        var approved=edits.decide(f.house(),f.owner(),requested.id(),new DecideSpaceEdit("approve",requested.version()),true);
        String id=delivery(f);
        var first=outbox.prepare(id,Instant.now());
        assertNotNull(first);
        outbox.finish(id,new PushResponse(503,Duration.ofSeconds(30)),Instant.now());
        assertEquals(id,deliveries.findById(id).orElseThrow().id);
        assertEquals(1,deliveries.findById(id).orElseThrow().attempts);
        var row=editRows.findById(approved.id()).orElseThrow();
        row.startBefore=Instant.now().minusSeconds(1);
        editRows.saveAndFlush(row);
        assertNull(outbox.prepare(id,Instant.now()));
        notice(f,"gone-device");
        id=delivery(f);
        outbox.finish(id,new PushResponse(410,Duration.ZERO),Instant.now());
        assertFalse(registration.status(f.family(),f.endpoint(),"test-session").enabled());
    }
}
