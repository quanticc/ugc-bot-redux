package com.ugcleague.ops.domain.document;

import com.ugcleague.ops.domain.util.PermissionProvider;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import sx.blah.discord.handle.obj.IRole;

import java.util.*;

public class DiscordRole extends AbstractAuditingEntity implements PermissionProvider {

    @Id
    private String id;
    @Indexed(sparse = true)
    private String name;
    @DBRef
    private Set<Permission> allowed = new LinkedHashSet<>();
    @DBRef
    private Set<Permission> denied = new LinkedHashSet<>();

    public DiscordRole() {

    }

    public DiscordRole(String id) {
        this.id = id;
    }

    public DiscordRole(IRole role) {
        this.id = role.getID();
        this.name = role.getName();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscordRole that = (DiscordRole) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiscordRole{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", allowed=" + allowed +
            ", denied=" + denied +
            //", events=" + events +
            '}';
    }
}
