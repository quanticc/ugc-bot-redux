package com.ugcleague.ops.web.rest;

public class SizzMatch {

    private String bluCountry;
    private String redCountry;
    private String hostname;
    private String bluname;
    private String redname;
    private int _id;
    private boolean isLive;
    private String created;

    public int getId() {
        return _id;
    }

    public boolean isLive() {
        return isLive;
    }

    public String getCreated() {
        return created;
    }
}
