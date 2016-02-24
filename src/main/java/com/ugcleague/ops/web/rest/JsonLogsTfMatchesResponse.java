package com.ugcleague.ops.web.rest;

import java.util.ArrayList;
import java.util.List;

public class JsonLogsTfMatchesResponse {

    private List<LogsTfMatch> logs = new ArrayList<>();
    private int results;
    private boolean success;

    public List<LogsTfMatch> getLogs() {
        return logs;
    }

    public int getResults() {
        return results;
    }

    public boolean isSuccess() {
        return success;
    }
}
