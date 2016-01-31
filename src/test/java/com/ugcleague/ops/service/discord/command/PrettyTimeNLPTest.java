package com.ugcleague.ops.service.discord.command;

import org.junit.Test;
import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.ocpsoft.prettytime.nlp.parse.DateGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

public class PrettyTimeNLPTest {

    private static final Logger log = LoggerFactory.getLogger(PrettyTimeNLPTest.class);

    @Test
    public void testBasicStringsParse() {
        List<DateGroup> parse = new PrettyTimeParser().parseSyntax("I eat fish every three days");
        assertFalse(parse.isEmpty());
        String formatted = new PrettyTime(Locale.ENGLISH).format(parse.get(0).getDates().get(0));
        assertEquals("3 days from now", formatted);
        assertEquals(1, parse.get(0).getLine());
        assertEquals(17, parse.get(0).getPosition());
        assertEquals(1, parse.get(0).getDates().size());
        assertNull(parse.get(0).getRecursUntil());
        assertTrue(parse.get(0).isRecurring());
        assertEquals(1000 * 60 * 60 * 24 * 3, parse.get(0).getRecurInterval()); // 3 days
    }

    @Test
    public void testSimpleTimeStrings() {
        LocalDateTime now = LocalDateTime.now();
        log.info("now: {}", now);
        Date yesterday = new PrettyTimeParser().parse("yesterday").get(0);
        log.info("yesterday -> {}", yesterday);
        Date twoHoursAgo = new PrettyTimeParser().parse("2 hours ago").get(0);
        log.info("2 hours ago -> {}", twoHoursAgo);
        Date lastWeek = new PrettyTimeParser().parse("last week").get(0);
        log.info("last week -> {}", lastWeek);
        Date aug122015 = new PrettyTimeParser().parse("august 12th 2015").get(0);
        log.info("august 12th 2015 -> {}", aug122015);
        Date yesterday10pm = new PrettyTimeParser().parse("yesterday at 10PM").get(0);
        log.info("yesterday at 10PM -> {}", yesterday10pm);
        Date real = new PrettyTimeParser().parse("2016-01-01 15:00:00").get(0);
        log.info("2016-01-01 15:00:00 -> {}", real);
        assertTrue(LocalDateTime.ofInstant(yesterday.toInstant(), ZoneId.systemDefault()).isAfter(now.minusDays(1)));
        assertTrue(LocalDateTime.ofInstant(twoHoursAgo.toInstant(), ZoneId.systemDefault()).isAfter(now.minusHours(2)));
        assertTrue(LocalDateTime.ofInstant(lastWeek.toInstant(), ZoneId.systemDefault()).isAfter(now.minusWeeks(1)));
        assertEquals(LocalDateTime.ofInstant(yesterday10pm.toInstant(), ZoneId.systemDefault()), now.minusDays(1).withHour(22).withMinute(0).withSecond(0).withNano(0));
        assertEquals(LocalDateTime.ofInstant(real.toInstant(), ZoneId.systemDefault()), LocalDateTime.of(2016, 1, 1, 15, 0, 0));
    }
}
