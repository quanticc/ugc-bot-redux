package com.ugcleague.ops.util;

import net.redhogs.cronparser.CronExpressionDescriptor;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DateUtil {

    private static final Logger log = LoggerFactory.getLogger(DateUtil.class);

    private static final LinkedHashMap<Long, Function<Duration, String>> past = new LinkedHashMap<>();
    private static final LinkedHashMap<Long, Function<Duration, String>> future = new LinkedHashMap<>();
    private static final LinkedHashMap<Long, Function<Duration, String>> present = new LinkedHashMap<>();

    static {
        long minute = 60;
        long hour = minute * 60;
        long day = hour * 24;
        long week = day * 7;
        long month = day * 30;
        long year = day * 365;
        past.put(2L, d -> "just now");
        past.put(minute, d -> d.get(ChronoUnit.SECONDS) + " seconds ago");
        past.put(minute * 2, d -> "a minute ago");
        past.put(hour, d -> d.toMinutes() + " minutes ago");
        past.put(hour * 2, d -> "an hour ago");
        past.put(day, d -> d.toHours() + " hours ago");
        past.put(day * 2, d -> "yesterday");
        past.put(week, d -> d.toDays() + " days ago");
        past.put(week * 2, d -> "last week");
        past.put(month, d -> d.toDays() / 7 + " weeks ago");
        past.put(month * 2, d -> "last month");
        past.put(year, d -> d.toDays() / 30 + " months ago");
        past.put(year * 2, d -> "last year");
        past.put(Long.MAX_VALUE, d -> d.toDays() / 365 + " years ago");
        future.put(2L, d -> "just now");
        future.put(minute, d -> d.get(ChronoUnit.SECONDS) + " seconds from now");
        future.put(minute * 2, d -> "a minute from now");
        future.put(hour, d -> d.toMinutes() + " minutes from now");
        future.put(hour * 2, d -> "an hour from now");
        future.put(day, d -> d.toHours() + " hours from now");
        future.put(day * 2, d -> "tomorrow");
        future.put(week, d -> d.toDays() + " days from now");
        future.put(week * 2, d -> "a week from now");
        future.put(month, d -> d.toDays() / 7 + " weeks from now");
        future.put(month * 2, d -> "a month from now");
        future.put(year, d -> d.toDays() / 30 + " months from now");
        future.put(year * 2, d -> "a year from now");
        future.put(Long.MAX_VALUE, d -> d.toDays() / 365 + " years from now");
        present.put(2L, d -> "second");
        present.put(minute, d -> d.get(ChronoUnit.SECONDS) + " seconds");
        present.put(minute * 2, d -> "minute");
        present.put(hour, d -> d.toMinutes() + " minutes");
        present.put(hour * 2, d -> "hour");
        present.put(day, d -> d.toHours() + " hours");
        present.put(day * 2, d -> "day");
        present.put(week, d -> d.toDays() + " days");
        present.put(week * 2, d -> "week");
        present.put(month, d -> d.toDays() / 7 + " weeks");
        present.put(month * 2, d -> "month");
        present.put(year, d -> d.toDays() / 30 + " months");
        present.put(year * 2, d -> "year");
        present.put(Long.MAX_VALUE, d -> d.toDays() / 365 + " years");
    }

    public static String formatRelativeBetweenNowAnd(Instant instant) {
        return formatRelative(Duration.between(Instant.now(), instant));
    }

    public static String formatRelative(Duration duration) {
        Duration abs = duration.abs();
        long seconds = abs.getSeconds();
        Map<Long, Function<Duration, String>> map = duration.isNegative() ? past : future;
        return map.entrySet().stream()
            .filter(e -> seconds < e.getKey())
            .map(e -> e.getValue().apply(abs))
            .findFirst().get();
    }

    public static String formatAbsolute(Duration duration) {
        Duration abs = duration.abs();
        long seconds = abs.getSeconds();
        if (seconds == 0) {
            return abs.toMillis() + " ms";
        }
        return present.entrySet().stream()
            .filter(e -> seconds < e.getKey())
            .map(e -> e.getValue().apply(abs))
            .findFirst().get();
    }

    public static String formatHuman(Duration duration) {
        return formatHuman(duration, false);
    }

    public static String formatHuman(Duration duration, boolean minimal) {
        Duration abs = duration.abs();
        long totalSeconds = abs.getSeconds();
        if (totalSeconds == 0) {
            return abs.toMillis() + (minimal ? "ms" : " milliseconds");
        }
        long d = totalSeconds / (3600 * 24);
        long h = (totalSeconds % (3600 * 24)) / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        String days = minimal ? compact(d, "d") : inflect(d, "day");
        String hours = minimal ? compact(h, "h") : inflect(h, "hour");
        String minutes = minimal ? compact(m, "m") : inflect(m, "minute");
        String seconds = minimal ? compact(s, "s") : inflect(s, "second");
        return Arrays.asList(days, hours, minutes, seconds).stream()
            .filter(str -> !str.isEmpty()).collect(Collectors.joining(minimal ? "" : ", "));
    }

    private static String compact(long value, String suffix) {
        return (value == 0 ? "" : value + suffix);
    }

    private static String inflect(long value, String singular) {
        return (value == 1 ? "1 " + singular : (value > 1 ? value + " " + singular + "s" : ""));
    }

    public static String formatElapsed(double seconds) {
        long totalSeconds = (long) seconds;
        return String.format(
            "%d:%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60);
    }

    public static String now(String format) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

    public static String formatMillis(final long millis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis));
        long hours = TimeUnit.MILLISECONDS.toHours(millis);

        return (hours == 0 ? "00" : hours < 10 ? String.valueOf("0" + hours) : String.valueOf(hours)) +
            ":" +
            (minutes == 0 ? "00" : minutes < 10 ? String.valueOf("0" + minutes) : String.valueOf(minutes)) +
            ":" +
            (seconds == 0 ? "00" : seconds < 10 ? String.valueOf("0" + seconds) : String.valueOf(seconds));
    }

    public static String humanizeCronPatterns(String patterns) {
        String[] array = patterns.split("\\|");
        if (array.length == 1) {
            return humanizeCronPattern(patterns);
        } else {
            return Arrays.asList(array).stream().map(DateUtil::humanizeCronPattern)
                .filter(s -> s != null).collect(Collectors.joining(". "));
        }
    }

    public static String humanizeCronPattern(String pattern) {
        try {
            return CronExpressionDescriptor.getDescription(pattern, Locale.ENGLISH);
        } catch (ParseException e) {
            return null;
        }
    }

    public static Instant nextValidTimeFromCron(String patterns) {
        String[] array = patterns.split("\\|");
        Instant next = Instant.MAX;
        for (String pattern : array) {
            if (pattern.split(" ").length == 5) {
                pattern = "0 " + pattern;
            }
            try {
                List<String> parts = Arrays.asList(pattern.split(" "));
                if (parts.get(3).equals("*")) {
                    parts.set(3, "?");
                }
                CronExpression cronExpression = new CronExpression(parts.stream().collect(Collectors.joining(" ")));
                Instant nextPart = cronExpression.getNextValidTimeAfter(Date.from(Instant.now())).toInstant();
                next = nextPart.isBefore(next) ? nextPart : next;
            } catch (ParseException e) {
                log.warn("Could not parse cron expression: {}", e.toString());
            }
        }
        return next;
    }

    public static String relativeNextTriggerFromCron(String patterns) {
        return formatRelativeBetweenNowAnd(nextValidTimeFromCron(patterns));
    }

    /**
     * Returns a corrected Date which originally has an incorrect time while keeping the same time-zone.
     *
     * @param incorrect the incorrect Date
     * @return a corrected ZonedDateTime, with the same time-zone as the given date but with the hour fixed.
     */
    public static ZonedDateTime correctOffsetSameZone(Date incorrect) {
        Instant instant = incorrect.toInstant();
        ZonedDateTime zoned = instant.atZone(ZoneId.systemDefault());
        return zoned.plusSeconds(zoned.getOffset().getTotalSeconds());
    }

    public static ZonedDateTime parseTimeDate(String s) {
        List<Date> parsed = new PrettyTimeParser().parse(s); // never null, can be empty
        if (!parsed.isEmpty()) {
            Date first = parsed.get(0);
            return ZonedDateTime.ofInstant(first.toInstant(), ZoneId.systemDefault());
        }
        log.warn("Could not parse a valid date from input: {}", s);
        return null;
    }

    private DateUtil() {

    }
}
