package com.ugcleague.ops.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DateUtil {

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
        Duration abs = duration.abs();
        long totalSeconds = abs.getSeconds();
        if (totalSeconds == 0) {
            return abs.toMillis() + " milliseconds";
        }
        long d = totalSeconds / (3600 * 24);
        long h = (totalSeconds % (3600 * 24)) / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        String days = inflect(d, "day");
        String hours = inflect(h, "hour");
        String minutes = inflect(m, "minute");
        String seconds = inflect(s, "second");
        return Arrays.asList(days, hours, minutes, seconds).stream()
            .filter(str -> !str.isEmpty()).collect(Collectors.joining(", "));
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

    private DateUtil() {

    }
}
