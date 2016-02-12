package com.ugcleague.ops.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class UgcPlayerPage {

    @JsonProperty("ugc_page")
    private String ugcPage = "";
    private List<Team> team = new ArrayList<>();

    public String getUgcPage() {
        return ugcPage;
    }

    public void setUgcPage(String ugcPage) {
        this.ugcPage = ugcPage;
    }

    public List<Team> getTeam() {
        return team;
    }

    public void setTeam(List<Team> team) {
        this.team = team;
    }

    public static class Team {

        private String name;
        private String tag;
        private String format;
        private String division;
        private String status;
        @JsonProperty("last_updated")
        private String lastUpdated;
        private String joined;
        private String active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getDivision() {
            return division;
        }

        public void setDivision(String division) {
            this.division = division;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getLastUpdated() {
            return lastUpdated;
        }

        public void setLastUpdated(String lastUpdated) {
            this.lastUpdated = lastUpdated;
        }

        public String getJoined() {
            return joined;
        }

        public void setJoined(String joined) {
            this.joined = joined;
        }

        public String getActive() {
            return active;
        }

        public void setActive(String active) {
            this.active = active;
        }
    }
}
