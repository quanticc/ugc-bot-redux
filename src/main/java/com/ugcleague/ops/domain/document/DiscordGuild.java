package com.ugcleague.ops.domain.document;

import com.ugcleague.ops.domain.util.PermissionProvider;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import sx.blah.discord.handle.obj.IGuild;

import java.util.*;

@Document(collection = "discord_guild")
public class DiscordGuild extends AbstractAuditingEntity implements PermissionProvider {

    @Id
    private String id;
    @Indexed(sparse = true)
    private String name;
    @DBRef
    private Set<DiscordChannel> channels = new LinkedHashSet<>();
    private Set<DiscordRole> roles = new LinkedHashSet<>();
    @DBRef
    private Set<Permission> allowed = new LinkedHashSet<>();
    @DBRef
    private Set<Permission> denied = new LinkedHashSet<>();

    public DiscordGuild() {

    }

    public DiscordGuild(String id) {
        this.id = id;
    }

    public DiscordGuild(IGuild guild) {
        this.id = guild.getID();
        this.name = guild.getName();
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

    public Set<DiscordChannel> getChannels() {
        return channels;
    }

    public void setChannels(Set<DiscordChannel> channels) {
        this.channels = channels;
    }

    public Set<DiscordRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<DiscordRole> roles) {
        this.roles = roles;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscordGuild that = (DiscordGuild) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiscordGuild{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", channels=" + channels +
            ", roles=" + roles +
            ", allowed=" + allowed +
            ", denied=" + denied +
            //", events=" + events +
            '}';
    }
}
