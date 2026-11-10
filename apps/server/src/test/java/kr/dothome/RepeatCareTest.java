package kr.dothome;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class RepeatCareTest {
    @Test void daylightSavingGapUsesNextValidMinuteAndOverlapUsesEarlierOffsetOnce(){
        var zone=ZoneId.of("America/New_York");
        var sunday=CareTime.bit(DayOfWeek.SUNDAY);
        var gap=new CareTime(zone,LocalTime.of(2,30),sunday);
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"),gap.on(LocalDate.of(2026,3,8)));
        var overlap=new CareTime(zone,LocalTime.of(1,30),sunday);
        var first=Instant.parse("2026-11-01T05:30:00Z");
        assertEquals(first,overlap.on(LocalDate.of(2026,11,1)));
        assertEquals(first,overlap.latest(Instant.parse("2026-11-01T06:30:00Z")));
        assertEquals(Instant.parse("2026-11-08T06:30:00Z"),overlap.next(first));
    }

    @Test void weekRuleFindsNextDayAndResolvesExactBoundary(){
        var time=new CareTime(ZoneId.of("Asia/Seoul"),LocalTime.of(9,0),CareTime.bit(DayOfWeek.MONDAY));
        var monday=Instant.parse("2026-11-02T00:00:00Z");
        assertEquals(monday,time.next(monday.minusNanos(1)));
        assertEquals(monday,time.latest(monday));
        assertEquals(Instant.parse("2026-11-09T00:00:00Z"),time.next(monday));
    }
}
