package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

@Document(collection = "incident")
public class Incident extends AbstractAuditingEntity {

    @Id
    private String id;
    private String group;
    private String name;
    private String status;
    @Field("created_at")
    private ZonedDateTime createdAt;
    @Field("updated_at")
    private ZonedDateTime updatedAt;
    @Field("monitoring_at")
    private ZonedDateTime monitoringAt;
    @Field("resolved_at")
    private ZonedDateTime resolvedAt;
    private String shortlink;
    @Field("page_id")
    private String pageId;
    @Field("incident_updates")
    private List<Update> incidentUpdates;
    private String impact;

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

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public List<Update> getIncidentUpdates() {
        return incidentUpdates;
    }

    public void setIncidentUpdates(List<Update> incidentUpdates) {
        this.incidentUpdates = incidentUpdates;
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Incident incident = (Incident) o;
        return Objects.equals(id, incident.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Incident{" +
            "id='" + id + '\'' +
            ", group='" + group + '\'' +
            ", name='" + name + '\'' +
            ", status='" + status + '\'' +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            ", monitoringAt=" + monitoringAt +
            ", resolvedAt=" + resolvedAt +
            ", shortlink='" + shortlink + '\'' +
            ", pageId='" + pageId + '\'' +
            ", incidentUpdates=" + incidentUpdates +
            ", impact='" + impact + '\'' +
            ", createdDate=" + getCreatedDate() +
            ", lastModifiedDate=" + getLastModifiedDate() +
            '}';
    }

    public static class Update {
        private String status;
        private String body;
        @Field("created_at")
        private ZonedDateTime createdAt;
        @Field("updated_at")
        private ZonedDateTime updatedAt;
        @Field("display_at")
        private ZonedDateTime displayAt;
        private String id;
        @Field("incident_id")
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Update update = (Update) o;
            return Objects.equals(id, update.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Update{" +
                "status='" + status + '\'' +
                ", body='" + body + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", displayAt=" + displayAt +
                ", id='" + id + '\'' +
                ", incidentId='" + incidentId + '\'' +
                '}';
        }
    }

}
