package com.ugcleague.ops.web.rest;

import java.util.Objects;

public class LogsTfMatch implements Comparable<LogsTfMatch> {

    private long date;
    private long id;
    private String title;

    public long getDate() {
        return date;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogsTfMatch that = (LogsTfMatch) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(LogsTfMatch o) {
        return -Long.compare(id, o.id);
    }
}
