package com.ugcleague.ops.web.rest;

import java.util.Objects;

public class SizzMatch {

    private String bluCountry;
    private String redCountry;
    private String hostname;
    private String bluname;
    private String redname;
    private long _id;
    private boolean isLive;
    private String created;

    public long getId() {
        return _id;
    }

    public boolean isLive() {
        return isLive;
    }

    public String getCreated() {
        return created;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SizzMatch sizzMatch = (SizzMatch) o;
        return _id == sizzMatch._id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id);
    }
}
