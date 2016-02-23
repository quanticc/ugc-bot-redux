package com.ugcleague.ops.web.rest;

import java.util.ArrayList;
import java.util.List;

public class JsonSizzMatchesResponse {

    private List<SizzMatch> matches = new ArrayList<>();

    public List<SizzMatch> getMatches() {
        return matches;
    }
}
