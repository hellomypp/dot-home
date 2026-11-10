package kr.dothome;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class MembershipLifecycleIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired MembershipLifecycle lifecycle;
    @Autowired HouseholdRequestService requests;
    @Autowired DependentService dependents;
    @Autowired CalendarService calendar;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"가족").id;}
    String house(String owner){return homes.create(owner,new Api.CreateHouse("가족 집","대표","sage")).household().id();}
    Api.World join(String house,String owner,String user){
        return homes.accept(homes.invite(house,owner).token(),user,new Api.Accept("가족","blue"));
    }

    @Test void exitKeepsOtherHomeAndHistoryButReadmissionUsesNewMembership(){
        String owner=user(),family=user(),house=house(owner),other=house(family);
        var original=homes.invite(house,owner);
        var earlier=homes.invite(house,owner);
        var initial=homes.accept(original.token(),family,new Api.Accept("가족","blue"));
        var member=initial.members().stream().filter(m->m.id().equals(initial.meMemberId())).findFirst().orElseThrow();
        var pet=homes.addPet(house,family,new Api.PetInput("모카","cat","cream")).pets().getFirst();
        var baby=dependents.save(house,family,null,new DependentInput("BABY","별이",null,null,null));
        homes.event(house,family,new Api.CreateEvent("event","NOTE","공유 기록"));
        var task=requests.create(house,owner,new CreateRequest("task",RequestKind.TASK,"집안일","",List.of(),null));
        requests.act(house,family,task.id(),"claim",new RequestAction("claim",task.version(),null));
        LocalDate date=LocalDate.of(2026,11,3);
        calendar.save(house,family,null,new CalendarInput("개인 일정","","GENERAL",true,date,date,null,null,"Asia/Seoul",0,"PERSONAL_SHARED"));
        calendar.save(house,family,null,new CalendarInput("공동 일정","","GENERAL",true,date,date,null,null,"Asia/Seoul",0,"HOUSEHOLD"));

        assertEquals(403,assertThrows(ApiException.class,()->lifecycle.leave(house,owner,new LeaveHouse("owner"))).status.value());
        assertEquals(409,assertThrows(ApiException.class,()->lifecycle.remove(house,owner,member.id(),member.membershipVersion()+1)).status.value());
        lifecycle.remove(house,owner,member.id(),member.membershipVersion());
        assertEquals(403,assertThrows(ApiException.class,()->homes.world(house,family)).status.value());
        assertEquals(other,homes.world(other,family).household().id());
        var remaining=homes.world(house,owner);
        assertEquals(1,remaining.members().size());
        assertEquals(remaining.meMemberId(),remaining.pets().stream().filter(p->p.id().equals(pet.id())).findFirst().orElseThrow().guardianMemberId());
        assertEquals(remaining.meMemberId(),remaining.dependents().stream().filter(p->p.id().equals(baby.id())).findFirst().orElseThrow().guardianMemberId());
        assertEquals("퇴실한 구성원",remaining.events().getFirst().authorName());
        assertEquals(RequestState.OPEN,requests.read(house,owner,task.id()).state());
        assertNull(requests.read(house,owner,task.id()).assigneeMemberId());
        assertEquals(List.of("공동 일정"),calendar.range(house,owner,date,date).stream().map(CalendarView::title).toList());
        for(var invite:List.of(original,earlier))
            assertEquals(410,assertThrows(ApiException.class,()->homes.accept(invite.token(),family,new Api.Accept("복귀","sage"))).status.value());

        var rejoined=join(house,owner,family);
        assertNotEquals(initial.meMemberId(),rejoined.meMemberId());
        assertEquals("퇴실한 구성원",rejoined.events().getFirst().authorName());
        assertEquals(410,assertThrows(ApiException.class,()->homes.accept(original.token(),family,new Api.Accept("복귀","sage"))).status.value());
        assertEquals(List.of("공동 일정"),calendar.range(house,family,date,date).stream().map(CalendarView::title).toList());
    }

    @Test void voluntaryLeaveReceiptCannotRemoveARejoinedFamilyMember(){
        String owner=user(),family=user(),house=house(owner);
        var original=join(house,owner,family);
        var input=new LeaveHouse("leave-once");
        lifecycle.leave(house,family,input);
        lifecycle.leave(house,family,input);
        var rejoined=join(house,owner,family);
        lifecycle.leave(house,family,input);
        assertEquals(rejoined.meMemberId(),homes.world(house,family).meMemberId());
        assertNotEquals(original.meMemberId(),rejoined.meMemberId());
    }
}
