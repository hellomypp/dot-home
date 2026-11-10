package kr.dothome;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class RepeatCareIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired RepeatCareService care;
    @Autowired HouseholdRequestService requests;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"가족").id;}

    @Test void restartAndConcurrentTicksCreateOneOccurrenceAndArchivingCancelsOpenCare() throws Exception {
        var owner=user();
        var guardian=user();
        var outsider=user();
        var house=homes.create(owner,new Api.CreateHouse("돌봄 집","대표","sage")).household().id();
        homes.accept(homes.invite(house,owner).token(),guardian,new Api.Accept("보호자","sage"));
        var pet=homes.addPet(house,guardian,new Api.PetInput("별이","cat","cream")).pets().getFirst();
        var input=new RepeatCareInput(CareProfileType.PET,pet.id(),"저녁 산책",List.of(DayOfWeek.values()),
            LocalTime.of(19,0),"Asia/Seoul",null);
        assertEquals(403,assertThrows(ApiException.class,()->care.save(house,outsider,null,input)).status.value());
        var rule=care.save(house,guardian,null,input);
        assertEquals(rule.id(),care.list(house,owner).getFirst().id());
        var late=rule.nextAt().plus(Duration.ofDays(4)).plusSeconds(30);
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            var first=pool.submit(()->{start.await();care.generate(rule.id(),late);return null;});
            var second=pool.submit(()->{start.await();care.generate(rule.id(),late);return null;});
            start.countDown();
            first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
        }
        var items=requests.list(house,owner,null,null,20).items();
        assertEquals(1,items.size());
        var occurrence=items.getFirst();
        assertEquals(late.minusSeconds(30),occurrence.careScheduledAt());
        assertEquals(RequestState.OPEN,occurrence.state());
        var next=care.list(house,guardian).getFirst();
        assertTrue(next.nextAt().isAfter(late));
        requests.act(house,guardian,occurrence.id(),"complete",new RequestAction("complete",occurrence.version(),null));
        care.generate(rule.id(),next.nextAt());
        assertEquals(2,requests.list(house,owner,null,null,20).items().size());
        homes.archivePet(house,guardian,pet.id(),pet.version());
        assertTrue(care.list(house,owner).isEmpty());
        care.generate(rule.id(),next.nextAt().plus(Duration.ofDays(1)));
        assertEquals(Set.of(RequestState.DONE,RequestState.CANCELLED),new HashSet<>(requests.list(house,owner,null,null,20)
            .items().stream().map(HouseholdRequestView::state).toList()));
        assertEquals(2,requests.list(house,owner,null,null,20).items().size());
        var done=requests.read(house,owner,occurrence.id());
        assertEquals(409,assertThrows(ApiException.class,()->requests.act(house,owner,done.id(),"reopen",
            new RequestAction("reopen",done.version(),"완료 취소"))).status.value());
    }

    @Test void nonGuardianReadsRulesButCannotEditAndOldWeeklyOccurrenceIsSkipped(){
        var owner=user();
        var reader=user();
        var house=homes.create(owner,new Api.CreateHouse("돌봄 집","대표","sage")).household().id();
        homes.accept(homes.invite(house,owner).token(),reader,new Api.Accept("가족","sage"));
        var pet=homes.addPet(house,owner,new Api.PetInput("별이","dog","cream")).pets().getFirst();
        var input=new RepeatCareInput(CareProfileType.PET,pet.id(),"저녁 산책",List.of(DayOfWeek.SUNDAY),
            LocalTime.of(19,0),"Asia/Seoul",null);
        var rule=care.save(house,owner,null,input);
        assertFalse(care.list(house,reader).getFirst().canEdit());
        assertEquals(403,assertThrows(ApiException.class,()->care.end(house,reader,rule.id(),rule.version())).status.value());
        care.generate(rule.id(),rule.nextAt().plus(Duration.ofDays(3)));
        assertTrue(requests.list(house,owner,null,null,20).items().isEmpty());
        assertEquals(rule.nextAt().plus(Duration.ofDays(7)),care.list(house,owner).getFirst().nextAt());
    }
}
