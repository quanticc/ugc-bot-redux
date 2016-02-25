package com.ugcleague.ops.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class SizzMatch {

    @JsonProperty("_id")
    private Long id;
    private String bluCountry;
    private String redCountry;
    private String hostname;
    private String bluname;
    private String redname;
    private String created;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getBluCountry() {
        return bluCountry;
    }

    public void setBluCountry(String bluCountry) {
        this.bluCountry = bluCountry;
    }

    public String getRedCountry() {
        return redCountry;
    }

    public void setRedCountry(String redCountry) {
        this.redCountry = redCountry;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getBluname() {
        return bluname;
    }

    public void setBluname(String bluname) {
        this.bluname = bluname;
    }

    public String getRedname() {
        return redname;
    }

    public void setRedname(String redname) {
        this.redname = redname;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SizzMatch sizzMatch = (SizzMatch) o;
        return Objects.equals(id, sizzMatch.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
