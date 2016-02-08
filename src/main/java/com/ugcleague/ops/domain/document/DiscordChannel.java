package com.ugcleague.ops.domain.document;

import com.ugcleague.ops.domain.util.PermissionProvider;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.*;

@Document(collection = "discord_channel")
public class DiscordChannel extends AbstractAuditingEntity implements PermissionProvider {

    @Id
    private String id;
    @Indexed(sparse = true)
    private String name;
    @Field("is_private")
    private boolean isPrivate;
    @DBRef
    private DiscordGuild guild;
    @DBRef
    private Set<DiscordMessage> message = new LinkedHashSet<>();
    @DBRef
    private Set<Permission> allowed = new LinkedHashSet<>();
    @DBRef
    private Set<Permission> denied = new LinkedHashSet<>();
    private List<Event> events = new ArrayList<>();

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

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public DiscordGuild getGuild() {
        return guild;
    }

    public void setGuild(DiscordGuild guild) {
        this.guild = guild;
    }

    public Set<DiscordMessage> getMessage() {
        return message;
    }

    public void setMessage(Set<DiscordMessage> message) {
        this.message = message;
    }

    public Set<Permission> getAllowed() {
        return allowed;
    }

    public void setAllowed(Set<Permission> allowed) {
        this.allowed = allowed;
    }

    public Set<Permission> getDenied() {
        return denied;
    }

    public void setDenied(Set<Permission> denied) {
        this.denied = denied;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscordChannel that = (DiscordChannel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiscordChannel{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", isPrivate=" + isPrivate +
            ", guild=" + (guild != null ? guild.getId() : "null") +
            ", message=" + message +
            ", allowed=" + allowed +
            ", denied=" + denied +
            //", events=" + events +
            '}';
    }
}
