package com.ugcleague.ops.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class DateUtil {

    private static final LinkedHashMap<Long, Function<Duration, String>> past = new LinkedHashMap<>();
    private static final LinkedHashMap<Long, Function<Duration, String>> future = new LinkedHashMap<>();

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

    private DateUtil() {

    }
}
