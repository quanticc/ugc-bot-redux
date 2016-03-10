package com.ugcleague.ops.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;
import java.util.List;

public class IncidentsResponse {

    private Page page;
    private List<Incident> incidents;

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public List<Incident> getIncidents() {
        return incidents;
    }

    public void setIncidents(List<Incident> incidents) {
        this.incidents = incidents;
    }

    public static class Page {

        private String id;
        private String name;
        private String url;
        @JsonProperty("updated_at")
        private ZonedDateTime updatedAt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public ZonedDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(ZonedDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class Incident {

        private String name;
        private String status;
        @JsonProperty("created_at")
        private ZonedDateTime createdAt;
        @JsonProperty("updated_at")
        private ZonedDateTime updatedAt;
        @JsonProperty("monitoring_at")
        private ZonedDateTime monitoringAt;
        @JsonProperty("resolved_at")
        private ZonedDateTime resolvedAt;
        private String shortlink;
        private String id;
        @JsonProperty("page_id")
        private String pageId;
        @JsonProperty("incident_updates")
        private List<IncidentUpdate> incidentUpdates;
        private String impact;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public ZonedDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(ZonedDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public ZonedDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(ZonedDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public ZonedDateTime getMonitoringAt() {
            return monitoringAt;
        }

        public void setMonitoringAt(ZonedDateTime monitoringAt) {
            this.monitoringAt = monitoringAt;
        }

        public ZonedDateTime getResolvedAt() {
            return resolvedAt;
        }

        public void setResolvedAt(ZonedDateTime resolvedAt) {
            this.resolvedAt = resolvedAt;
        }

        public String getShortlink() {
            return shortlink;
        }

        public void setShortlink(String shortlink) {
            this.shortlink = shortlink;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPageId() {
            return pageId;
        }

        public void setPageId(String pageId) {
            this.pageId = pageId;
        }

        public List<IncidentUpdate> getIncidentUpdates() {
            return incidentUpdates;
        }

        public void setIncidentUpdates(List<IncidentUpdate> incidentUpdates) {
            this.incidentUpdates = incidentUpdates;
        }

        public String getImpact() {
            return impact;
        }

        public void setImpact(String impact) {
            this.impact = impact;
        }
    }

    public static class IncidentUpdate {

        private String status;
        private String body;
        @JsonProperty("created_at")
        private ZonedDateTime createdAt;
        @JsonProperty("updated_at")
        private ZonedDateTime updatedAt;
        @JsonProperty("display_at")
        private ZonedDateTime displayAt;
        private String id;
        @JsonProperty("incident_id")
        private String incidentId;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public ZonedDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(ZonedDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public ZonedDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(ZonedDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public ZonedDateTime getDisplayAt() {
            return displayAt;
        }

        public void setDisplayAt(ZonedDateTime displayAt) {
            this.displayAt = displayAt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getIncidentId() {
            return incidentId;
        }

        public void setIncidentId(String incidentId) {
            this.incidentId = incidentId;
        }
    }
}
