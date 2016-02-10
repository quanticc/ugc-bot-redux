package com.ugcleague.ops.domain.document;

import java.util.LinkedHashMap;
import java.util.Map;

public class Event extends AbstractAuditingEntity {

    private String type;

    private Map<String, Object> properties = new LinkedHashMap<>();

    public Event() {

    }

    public Event(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "Event{" +
            ", type='" + type + '\'' +
            ", properties=" + properties +
            ", created=" + getCreatedDate() +
            '}';
    }
}
