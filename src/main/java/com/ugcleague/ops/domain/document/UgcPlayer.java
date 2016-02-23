package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Partial representation of UGC player data
 */
@Document(collection = "ugc_player")
public class UgcPlayer extends AbstractAuditingEntity {

    /*
    id = steamID64
     */
    @Id
    private Long id;
    private String name;
    private String type;
    private ZonedDateTime added;
    private ZonedDateTime updated;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ZonedDateTime getAdded() {
        return added;
    }

    public void setAdded(ZonedDateTime added) {
        this.added = added;
    }

    public ZonedDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(ZonedDateTime updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UgcPlayer ugcPlayer = (UgcPlayer) o;
        return Objects.equals(id, ugcPlayer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UgcPlayer{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", type='" + type + '\'' +
            ", added=" + added +
            ", updated=" + updated +
            ", cacheCreated=" + getCreatedDate() +
            ", cacheLastModified=" + getLastModifiedDate() +
            '}';
    }
}
