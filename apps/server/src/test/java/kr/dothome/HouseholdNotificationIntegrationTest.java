package kr.dothome;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest @ActiveProfiles({"dev","test"})
class HouseholdNotificationIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identities;
    @Autowired HouseholdRequestService requests;
    @Autowired HouseholdNotificationService notifications;
    @Autowired SpaceEditAccess editAccess;
    @Autowired MembershipLifecycle membership;
    @Autowired NotificationPreferenceService preferences;

    private String user(){return identities.resolve("test",UUID.randomUUID().toString(),"알림 검증").id;}
    private String create(String owner){return homes.create(owner,new Api.CreateHouse("알림 집","대표","sage")).household().id();}
    private String join(String h,String owner,String user){return homes.accept(homes.invite(h,owner).token(),user,new Api.Accept("가족","sage")).meMemberId();}

    @Test void scopedEventsAreDeduplicatedAndEachRecipientReadsIndependently()throws Exception{
        String owner=user(),first=user(),second=user(),stranger=user(),h=create(owner);
        String recipient=join(h,owner,first);
        join(h,owner,second);
        var task=new CreateRequest("task",RequestKind.TASK,"대상 가족 부탁","내용",List.of(recipient),null);
        requests.create(h,owner,task);
        requests.create(h,owner,task);
        requests.create(h,owner,new CreateRequest("notice",RequestKind.NOTICE,"가족 공지","내용",List.of(),null));
        var page=notifications.list(h,first,null,1);
        assertEquals(2,page.unreadCount());
        assertNotNull(page.nextCursor());
        var older=notifications.list(h,first,page.nextCursor(),1);
        assertEquals(NotificationKind.FAMILY_TASK,older.items().getFirst().kind());
        assertNull(older.nextCursor());
        assertEquals(1,notifications.list(h,second,null,10).unreadCount());
        assertTrue(notifications.list(h,owner,null,10).items().isEmpty());
        assertEquals(403,assertThrows(ApiException.class,()->notifications.list(h,stranger,null,10)).status.value());
        assertEquals(400,assertThrows(ApiException.class,()->notifications.list(h,second,page.nextCursor(),10)).status.value());
        String item=page.items().getFirst().id();
        assertEquals(404,assertThrows(ApiException.class,()->notifications.read(item,second)).status.value());

        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<NotificationView> read=()->{start.await();return notifications.read(item,first);};
            var a=pool.submit(read);
            var b=pool.submit(read);
            start.countDown();
            var saved=a.get(10,TimeUnit.SECONDS);
            assertNotNull(saved.readAt());
            assertEquals(saved.readAt(),b.get(10,TimeUnit.SECONDS).readAt());
        }
        assertEquals(1,notifications.list(h,first,null,10).unreadCount());
        assertEquals(1,notifications.list(h,second,null,10).unreadCount());
        membership.leave(h,first,new LeaveHouse("leave"));
        join(h,owner,first);
        assertEquals(404,assertThrows(ApiException.class,()->notifications.read(item,first)).status.value());
        assertTrue(notifications.list(h,first,null,10).items().isEmpty());
    }

    @Test void editRequestsAndDecisionsOnlyNotifyTheirParticipants(){
        String owner=user(),family=user(),other=user(),h=create(owner);
        join(h,owner,family);
        join(h,owner,other);
        var input=new AskSpaceEdit("ask",Set.of(SpaceEditScope.PRESET));
        var request=editAccess.ask(h,family,input);
        editAccess.ask(h,family,input);
        assertEquals(List.of(NotificationKind.EDIT_REQUESTED),notifications.list(h,owner,null,10).items().stream().map(NotificationView::kind).toList());
        var approval=new DecideSpaceEdit("allow",request.version());
        var approved=editAccess.decide(h,owner,request.id(),approval,true);
        editAccess.decide(h,owner,request.id(),approval,true);
        editAccess.cancel(h,owner,approved.id(),approved.version());
        var received=notifications.list(h,family,null,10);
        assertEquals(2,received.unreadCount());
        assertEquals(Set.of(NotificationKind.EDIT_APPROVED,NotificationKind.EDIT_REVOKED),
            new HashSet<>(received.items().stream().map(NotificationView::kind).toList()));
        assertTrue(notifications.list(h,other,null,10).items().isEmpty());
    }

    @Test void preferencesAreVersionedPerMembershipAndOnlySuppressNewSelectedKinds(){
        String owner=user(),family=user(),other=user(),h=create(owner),second=create(owner);
        join(h,owner,family);
        join(h,owner,other);
        join(second,owner,family);
        requests.create(h,owner,new CreateRequest("old",RequestKind.TASK,"기존 부탁","",List.of(),null));
        var saved=preferences.patch(h,family,new ChangeNotificationSettings(Map.of(NotificationGroup.TASK,false),0L));
        assertEquals(1,saved.version());
        assertFalse(saved.enabled().get(NotificationGroup.TASK));
        assertTrue(saved.enabled().get(NotificationGroup.NOTICE));
        assertTrue(preferences.get(second,family).enabled().get(NotificationGroup.TASK));
        assertEquals(409,assertThrows(ApiException.class,()->preferences.patch(h,family,
            new ChangeNotificationSettings(Map.of(NotificationGroup.NOTICE,false),0L))).status.value());
        assertEquals(403,assertThrows(ApiException.class,()->preferences.get(second,other)).status.value());
        assertEquals(saved,preferences.patch(h,family,new ChangeNotificationSettings(Map.of(NotificationGroup.TASK,false),1L)));
        requests.create(h,owner,new CreateRequest("new-task",RequestKind.TASK,"새 부탁","",List.of(),null));
        requests.create(h,owner,new CreateRequest("new-notice",RequestKind.NOTICE,"새 공지","",List.of(),null));
        assertEquals(List.of(NotificationKind.FAMILY_NOTICE,NotificationKind.FAMILY_TASK),
            notifications.list(h,family,null,10).items().stream().map(NotificationView::kind).toList());
        assertEquals(3,notifications.list(h,other,null,10).unreadCount());
        preferences.patch(h,family,new ChangeNotificationSettings(Map.of(NotificationGroup.TASK,true),1L));
        requests.create(h,owner,new CreateRequest("enabled",RequestKind.TASK,"다시 받는 부탁","",List.of(),null));
        assertEquals(3,notifications.list(h,family,null,10).unreadCount());
    }
}
