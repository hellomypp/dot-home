package kr.dothome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
class V06RulesTest {
 @Test void fridgeAllowlistCannotBeUsedForPathInjection(){assertDoesNotThrow(()->ObjectNoteService.object("fridge"));for(String object:new String[]{"sofa","../fridge","fridge/notes","FRIDGE"})assertThrows(ApiException.class,()->ObjectNoteService.object(object));}
 @Test void calendarRejectsUnknownZoneAndMissingBoundaries(){var day=LocalDate.of(2026,11,3);assertThrows(ApiException.class,()->CalendarService.validate(new CalendarInput("x","","GENERAL",true,day,day,null,null,"Not/AZone",0)));assertThrows(ApiException.class,()->CalendarService.validate(new CalendarInput("x","","GENERAL",false,null,null,null,null,"Asia/Seoul",0)));}
 @Test void housewarmingReturnPathsAreInternalOnly(){String path="/visit/"+"a".repeat(43);assertEquals(path,GoogleLoginFlow.safeNext(path));for(String value:new String[]{path+"?next=evil",path+"\n",path+"\r\n",path+"#fragment","https://example.com"+path,"//example.com"+path})assertNull(GoogleLoginFlow.safeNext(value));}
}
