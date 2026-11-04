package kr.dothome;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class HouseholdRequestsIntegrationTest {
    @Autowired HouseholdRequestService service;
    @Autowired HouseholdRequests rows;
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired ObjectMapper json;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"검증 가족").id;}
    String house(String owner){return homes.create(owner,new Api.CreateHouse("검증 집","대표","sage")).household().id();}
    String join(String h,String owner){
        String guest=user();
        homes.accept(homes.invite(h,owner).token(),guest,new Api.Accept("가족","sage"));
        return guest;
    }
    CreateRequest task(String id,List<String> targets){
        return new CreateRequest(id,RequestKind.TASK,"간식 준비","과일을 씻어 주세요.",targets,null);
    }
    @Test void creationIsIdempotentAndScopesTargetsAndReads(){
        String a=user(),h=house(a),b=join(h,a),outsider=user(),other=house(outsider);
        var target=homes.requireMember(h,b).id;
        var input=task("same",List.of(target));
        var created=service.create(h,a,input);
        assertEquals(created,service.create(h,a,input));
        assertEquals(409,assertThrows(ApiException.class,()->service.create(h,a,task("same",List.of()))).status.value());
        assertEquals(400,assertThrows(ApiException.class,()->service.create(h,a,task("foreign",List.of(homes.requireMember(other,outsider).id)))).status.value());
        assertEquals(403,assertThrows(ApiException.class,()->service.read(h,outsider,created.id())).status.value());
        assertEquals(403,assertThrows(ApiException.class,()->service.read(other,outsider,created.id())).status.value());
        assertEquals(403,assertThrows(ApiException.class,()->service.act(h,a,created.id(),"claim",new RequestAction("not-target",0L,null))).status.value());
        var claimed=service.act(h,b,created.id(),"claim",new RequestAction("claim",0L,null));
        assertEquals(target,claimed.assigneeMemberId());
        assertEquals(claimed,service.act(h,b,created.id(),"claim",new RequestAction("claim",0L,null)));
        assertEquals(2,service.history(h,b,created.id()).size());
    }
    @Test void simultaneousClaimHasOneWinnerAndReleaseReopens()throws Exception {
        String owner=user(),h=house(owner),a=join(h,owner),b=join(h,owner);
        var task=service.create(h,owner,task("create",List.of()));
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            var futures=List.of(a,b).stream().map(user->pool.submit(()->{
                start.await();
                try {service.act(h,user,task.id(),"claim",new RequestAction("claim",0L,null));return user;}
                catch(ApiException e){assertEquals("ALREADY_CLAIMED",e.code);return "";}
            })).toList();
            var users=List.of(futures.get(0),futures.get(1));
            start.countDown();
            var winners=new ArrayList<String>();
            for(var future:users){var won=future.get(10,TimeUnit.SECONDS);if(!won.isEmpty())winners.add(won);}
            assertEquals(1,winners.size());
            var current=service.read(h,owner,task.id());
            var opened=service.act(h,winners.getFirst(),task.id(),"release",new RequestAction("release",current.version(),null));
            assertEquals(RequestState.OPEN,opened.state());
            assertNull(opened.assigneeMemberId());
        }
    }
    @Test void completedRequestsKeepHistoryAndDoNotExpireUntilReopened(){
        String a=user(),h=house(a),b=join(h,a);
        var task=service.create(h,a,task("create",List.of()));
        var done=service.act(h,b,task.id(),"complete",new RequestAction("done",task.version(),null));
        assertEquals(RequestState.DONE,done.state());
        assertNotNull(done.completedAt());
        var row=rows.findById(task.id()).orElseThrow();
        row.dueAt=Instant.now().minusSeconds(1);
        rows.saveAndFlush(row);
        var current=service.read(h,b,row.id);
        assertEquals(RequestState.DONE,current.state());
        var reopened=service.act(h,a,row.id,"reopen",new RequestAction("reopen",current.version(),"다시 확인"));
        assertEquals(RequestState.EXPIRED,reopened.state());
        assertEquals(List.of(RequestState.OPEN,RequestState.DONE,RequestState.EXPIRED),
            service.history(h,a,row.id).stream().map(RequestHistoryItem::nextState).toList());
        var page=service.list(h,a,List.of(RequestState.EXPIRED),null,20);
        assertEquals(List.of(row.id),page.items().stream().map(HouseholdRequestView::id).toList());
    }
    @Test void requestEditPreservesOtherFieldsAndProtectsAssignee(){
        String owner=user(),h=house(owner),author=join(h,owner),assignee=join(h,owner);
        String target=homes.requireMember(h,assignee).id;
        var task=service.create(h,author,task("create",List.of(target)));
        assertEquals(403,assertThrows(ApiException.class,()->service.edit(h,assignee,task.id(),json.readTree("""
            {"version":0,"title":"변경"}
            """))).status.value());
        var claimed=service.act(h,assignee,task.id(),"claim",new RequestAction("claim",task.version(),null));
        var edited=service.edit(h,owner,task.id(),json.createObjectNode().put("version",claimed.version()).put("title","제목만 바꾸기"));
        assertEquals(task.body(),edited.body());
        assertEquals(task.dueAt(),edited.dueAt());
        assertEquals(List.of(target),edited.targetMemberIds());
        assertEquals(target,edited.assigneeMemberId());
        var replace=json.createObjectNode().put("version",edited.version());
        replace.putArray("targetMemberIds").add(homes.requireMember(h,owner).id);
        assertEquals(409,assertThrows(ApiException.class,()->service.edit(h,author,task.id(),replace)).status.value());
        assertEquals(409,assertThrows(ApiException.class,()->service.edit(h,author,task.id(),json.createObjectNode().put("version",0).put("title","오래된 수정"))).status.value());
    }
    @Test void noticeAcknowledgementIsPrivateAndIdempotent(){
        String owner=user(),h=house(owner),a=join(h,owner),b=join(h,owner);
        var notice=service.create(h,owner,new CreateRequest("notice",RequestKind.NOTICE,"확인해 주세요",null,null,null));
        assertFalse(service.read(h,a,notice.id()).acknowledgedByMe());
        var acknowledged=service.acknowledge(h,a,notice.id());
        assertEquals(acknowledged,service.acknowledge(h,a,notice.id()));
        assertTrue(service.read(h,a,notice.id()).acknowledgedByMe());
        assertTrue(service.list(h,a,null,null,20).items().getFirst().acknowledgedByMe());
        assertFalse(service.read(h,b,notice.id()).acknowledgedByMe());
        assertFalse(service.read(h,owner,notice.id()).acknowledgedByMe());
        assertEquals(403,assertThrows(ApiException.class,()->service.acknowledge(h,user(),notice.id())).status.value());
        assertEquals(notice.version(),service.read(h,owner,notice.id()).version());
    }
    @Test void expiryFilteringAndNoticeAndPromiseStatesAreDistinct(){
        String a=user(),h=house(a);
        var notice=service.create(h,a,new CreateRequest("notice",RequestKind.NOTICE,"정리 안내",null,null,null));
        var promise=service.create(h,a,new CreateRequest("promise",RequestKind.PROMISE,"내가 정리할게요",null,null,null));
        assertEquals(RequestState.ACTIVE,notice.state());
        assertEquals(RequestState.CLAIMED,promise.state());
        assertEquals(homes.requireMember(h,a).id,promise.assigneeMemberId());
        assertEquals(400,assertThrows(ApiException.class,()->service.act(h,a,notice.id(),"complete",new RequestAction("wrong",0L,null))).status.value());
        var row=rows.findById(notice.id()).orElseThrow();
        row.dueAt=Instant.now().minusSeconds(1);rows.saveAndFlush(row);
        assertTrue(service.list(h,a,List.of(RequestState.ACTIVE),null,20).items().isEmpty());
        assertEquals(notice.id(),service.list(h,a,List.of(RequestState.EXPIRED),null,1).items().getFirst().id());
        assertEquals(410,assertThrows(ApiException.class,()->service.act(h,a,notice.id(),"cancel",new RequestAction("late",0L,"종료"))).status.value());
    }
}
