package com.ugcleague.ops.web.rest;

import com.ugcleague.ops.domain.document.SizzStats;

public class JsonSizzStatsResponse {

    private SizzStats stats;

    public SizzStats getStats() {
        return stats;
    }

    public void setStats(SizzStats stats) {
        this.stats = stats;
    }
}
