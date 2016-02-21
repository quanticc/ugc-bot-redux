package com.ugcleague.ops.service.discord;

import org.junit.Test;

import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

public class SupportTimeTest {

    @Test
    public void testCrossDayPeriod() throws Exception {
        ZonedDateTime inside = ZonedDateTime.of(2016, 1, 5, 23, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime before = ZonedDateTime.of(2016, 1, 5, 15, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime after = ZonedDateTime.of(2016, 1, 6, 7, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime start = ZonedDateTime.of(2016, 1, 1, 20, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime finish = ZonedDateTime.of(2016, 1, 2, 2, 0, 0, 0, ZoneId.systemDefault());
        Period diffInside = Period.between(start.toLocalDate(), inside.toLocalDate());
        Period diffBefore = Period.between(start.toLocalDate(), before.toLocalDate());
        Period diffAfter = Period.between(start.toLocalDate(), after.toLocalDate());
        assertTrue(inside.isAfter(start.plus(diffInside)) && inside.isBefore(finish.plus(diffInside)));
        assertFalse(before.isAfter(start.plus(diffBefore)) && before.isBefore(finish.plus(diffBefore)));
        assertFalse(after.isAfter(start.plus(diffAfter)) && after.isBefore(finish.plus(diffAfter)));
    }

    @Test
    public void testSameDayPeriod() throws Exception {
        ZonedDateTime inside = ZonedDateTime.of(2016, 1, 5, 5, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime before = ZonedDateTime.of(2016, 1, 5, 23, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime after = ZonedDateTime.of(2016, 1, 5, 7, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime start = ZonedDateTime.of(2016, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime finish = ZonedDateTime.of(2016, 1, 1, 6, 0, 0, 0, ZoneId.systemDefault());
        Period diffInside = Period.between(start.toLocalDate(), inside.toLocalDate());
        Period diffBefore = Period.between(start.toLocalDate(), before.toLocalDate());
        Period diffAfter = Period.between(start.toLocalDate(), after.toLocalDate());
        assertTrue(inside.isAfter(start.plus(diffInside)) && inside.isBefore(finish.plus(diffInside)));
        assertFalse(before.isAfter(start.plus(diffBefore)) && before.isBefore(finish.plus(diffBefore)));
        assertFalse(after.isAfter(start.plus(diffAfter)) && after.isBefore(finish.plus(diffAfter)));
    }

    @Test
    public void testOffsetTimeFormatting() throws Exception {
        ZonedDateTime minus5 = ZonedDateTime.of(2016, 1, 5, 5, 30, 0, 0, ZoneId.of("UTC-5"));
        ZonedDateTime eastern = ZonedDateTime.of(2016, 1, 5, 23, 0, 0, 0, ZoneId.of("America/New_York"));
        assertEquals("05:30 UTC-05:00", minus5.format(DateTimeFormatter.ofPattern("HH:mm z")));
        assertEquals("23:00 EST", eastern.format(DateTimeFormatter.ofPattern("HH:mm z")));
    }
}
