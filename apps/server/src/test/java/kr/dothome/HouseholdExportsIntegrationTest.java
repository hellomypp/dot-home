package kr.dothome;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"dev","test"})
class HouseholdExportsIntegrationTest {
    @Autowired HomeService homes;
    @Autowired IdentityService identity;
    @Autowired HouseholdExportService exports;
    @Autowired HouseholdExports rows;
    @Autowired MembershipLifecycle membership;
    @Autowired ObjectNoteService notes;
    @Autowired CalendarService calendar;
    @Autowired HouseholdRequestService requests;
    @Autowired ObjectMapper json;

    String user(){return identity.resolve("test",UUID.randomUUID().toString(),"가족").id;}

    @Test void jobIncludesSharedRecordsOnlyAndBecomesInaccessibleAfterLeaving() {
        var owner=user();
        var family=user();
        var outsider=user();
        var house=homes.create(owner,new Api.CreateHouse("가족 집","대표","sage")).household().id();
        var member=homes.accept(homes.invite(house,owner).token(),family,new Api.Accept("가족","sage")).meMemberId();
        var otherHouse=homes.create(family,new Api.CreateHouse("다른 집","가족","sage")).household().id();
        notes.create(house,owner,"fridge",new NoteInput("내보낼 가족 쪽지"));
        requests.create(house,owner,new CreateRequest("request",RequestKind.TASK,"함께 할 일","공동 요청",List.of(),null));
        homes.event(house,family,new Api.CreateEvent("event","NOTE","공동 생활 기록"));
        homes.status(house,family,new Api.Status(null,null,null,"현재 상태 제외",60));
        var date=LocalDate.of(2026,11,3);
        calendar.save(house,owner,null,new CalendarInput("공동 일정","","GENERAL",true,date,date,null,null,"Asia/Seoul",0,"HOUSEHOLD"));
        calendar.save(house,owner,null,new CalendarInput("개인 공유 제외","","GENERAL",true,date,date,null,null,"Asia/Seoul",0,"PERSONAL_SHARED"));
        assertThrows(ApiException.class,()->exports.start(house,outsider,new ExportInput("invalid")));

        var job=exports.start(house,family,new ExportInput("first"));
        assertEquals(job.id(),exports.start(house,family,new ExportInput("first")).id());
        assertEquals(429,assertThrows(ApiException.class,()->exports.start(house,family,new ExportInput("new"))).status.value());
        assertThrows(ApiException.class,()->exports.status(house,owner,job.id()));
        assertThrows(ApiException.class,()->exports.status(otherHouse,family,job.id()));
        exports.generate(job.id());
        var ready=exports.status(house,family,job.id());
        assertEquals(ExportState.READY,ready.state());
        assertNull(exports.latest(house,family).getFirst().downloadToken());
        assertEquals(410,assertThrows(ApiException.class,()->exports.download(house,family,job.id(),"1."+ready.downloadToken().split("\\.")[1])).status.value());
        var raw=exports.download(house,family,job.id(),ready.downloadToken());
        var doc=json.readTree(raw);
        assertEquals("내보낼 가족 쪽지",doc.get("notes").get(0).get("body").asText());
        assertEquals("공동 일정",doc.get("calendar").get(0).get("title").asText());
        assertEquals(1,doc.get("calendar").size());
        assertEquals("함께 할 일",doc.get("requests").get(0).get("title").asText());
        assertEquals("공동 생활 기록",doc.get("lifeEvents").get(0).get("message").asText());
        for(var excluded:List.of("개인 공유 제외","현재 상태 제외",member,family,owner))assertFalse(raw.contains(excluded));

        membership.leave(house,family,new LeaveHouse("leave"));
        assertNull(rows.findById(job.id()).orElseThrow().payload);
        assertThrows(ApiException.class,()->exports.download(house,family,job.id(),ready.downloadToken()));
        homes.accept(homes.invite(house,owner).token(),family,new Api.Accept("가족","sage"));
        assertThrows(ApiException.class,()->exports.download(house,family,job.id(),ready.downloadToken()));
    }
}
