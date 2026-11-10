package kr.dothome;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class OwnershipIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired OwnershipService ownership;
    @Autowired MembershipLifecycle membership;
    @Autowired JdbcTemplate jdbc;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"가족").id;}
    String house(String owner){return homes.create(owner,new Api.CreateHouse("가족 집","대표","sage")).household().id();}
    String join(String h,String owner,String user){
        return homes.accept(homes.invite(h,owner).token(),user,new Api.Accept("가족","sage")).meMemberId();
    }

    @Test void onlyRecipientMayConfirmAndOldOwnerCanLeaveAfterTransfer(){
        var owner=user();
        var target=user();
        var bystander=user();
        var h=house(owner);
        var targetId=join(h,owner,target);
        join(h,owner,bystander);
        var input=new ProposeOwnership("proposal",targetId);
        var offer=ownership.propose(h,owner,input);
        assertEquals(offer,ownership.propose(h,owner,input));
        assertEquals(List.of(offer),ownership.current(h,target));
        assertTrue(ownership.current(h,bystander).isEmpty());
        assertEquals(403,assertThrows(ApiException.class,()->ownership.decide(h,owner,offer.id(),new DecideOwnership(0L,true),true)).status.value());
        assertEquals(400,assertThrows(ApiException.class,()->ownership.decide(h,target,offer.id(),new DecideOwnership(0L,false),true)).status.value());
        assertEquals(409,assertThrows(ApiException.class,()->ownership.decide(h,target,offer.id(),new DecideOwnership(1L,true),true)).status.value());
        var accepted=ownership.decide(h,target,offer.id(),new DecideOwnership(0L,true),true);
        assertEquals(TransferState.ACCEPTED,accepted.state());
        assertEquals(accepted,ownership.decide(h,target,offer.id(),new DecideOwnership(0L,true),true));
        var world=homes.world(h,target);
        assertEquals(target,world.household().ownerId());
        assertEquals(List.of(targetId),world.members().stream().filter(m->m.role().equals("OWNER")).map(Api.MemberView::id).toList());
        assertEquals(1,world.members().stream().filter(m->m.id().equals(targetId)).findFirst().orElseThrow().membershipVersion());
        membership.leave(h,owner,new LeaveHouse("old-owner"));
        assertEquals(2,homes.world(h,target).members().size());
        assertEquals(403,assertThrows(ApiException.class,()->membership.leave(h,target,new LeaveHouse("new-owner"))).status.value());
    }

    @Test void expiredAndDepartedProposalsCannotChangeOwner() {
        var owner=user();
        var target=user();
        var h=house(owner);
        var targetId=join(h,owner,target);
        var first=ownership.propose(h,owner,new ProposeOwnership("first",targetId));
        jdbc.update("UPDATE ownership_transfer SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?",first.id());
        assertEquals(TransferState.EXPIRED,ownership.current(h,target).getFirst().state());
        assertEquals(410,assertThrows(ApiException.class,()->ownership.decide(h,target,first.id(),new DecideOwnership(0L,true),true)).status.value());
        ownership.propose(h,owner,new ProposeOwnership("second",targetId));
        membership.leave(h,target,new LeaveHouse("leave"));
        assertTrue(ownership.current(h,owner).isEmpty());
        assertEquals(owner,homes.world(h,owner).household().ownerId());
        var rejoined=join(h,owner,target);
        assertNotEquals(targetId,rejoined);
        assertTrue(ownership.current(h,target).isEmpty());
    }

    @Test void parallelProposalsLeaveOnePendingRecipient()throws Exception{
        var owner=user();
        var h=house(owner);
        var first=join(h,owner,user());
        var second=join(h,owner,user());
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            var calls=List.of(first,second).stream().map(id->pool.submit(()->{
                start.await();
                try{ownership.propose(h,owner,new ProposeOwnership(id,id));return 1;}
                catch(ApiException e){assertEquals(409,e.status.value());return 0;}
            })).toList();
            start.countDown();
            assertEquals(1,calls.getFirst().get(5,TimeUnit.SECONDS)+calls.getLast().get(5,TimeUnit.SECONDS));
        }
        assertEquals(1,ownership.current(h,owner).size());
    }
}
