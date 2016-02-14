package com.ugcleague.ops.domain.document;

import com.ugcleague.ops.domain.util.PermissionProvider;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import sx.blah.discord.handle.obj.IUser;

import java.time.ZonedDateTime;
import java.util.*;

@Document(collection = "discord_user")
public class DiscordUser extends AbstractAuditingEntity implements PermissionProvider {

    @Id
    private String id;
    @Indexed(sparse = true)
    private String name;
    @DBRef
    private Set<Permission> allowed = new LinkedHashSet<>();
    @DBRef
    private Set<Permission> denied = new LinkedHashSet<>();
    private List<Event> events = new ArrayList<>();
    @Field("last_connect")
    private ZonedDateTime lastConnect = ZonedDateTime.now();
    @Field("last_disconnect")
    private ZonedDateTime lastDisconnect = ZonedDateTime.now();

    public DiscordUser() {

    }

    public DiscordUser(String id) {
        this.id = id;
    }

    public DiscordUser(IUser user) {
        this.id = user.getID();
        this.name = user.getName();
    }

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

    public ZonedDateTime getLastConnect() {
        return lastConnect;
    }

    public void setLastConnect(ZonedDateTime lastConnect) {
        this.lastConnect = lastConnect;
    }

    public ZonedDateTime getLastDisconnect() {
        return lastDisconnect;
    }

    public void setLastDisconnect(ZonedDateTime lastDisconnect) {
        this.lastDisconnect = lastDisconnect;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscordUser that = (DiscordUser) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiscordUser{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", allowed=" + allowed +
            ", denied=" + denied +
            //", events=" + events +
            ", lastConnect=" + lastConnect +
            ", lastDisconnect=" + lastDisconnect +
            '}';
    }
}
