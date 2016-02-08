package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.validation.constraints.NotNull;
import java.util.Objects;

@Document(collection = "permission")
public class Permission extends AbstractAuditingEntity {

    @Id
    private String id;

    @Field("name")
    @Indexed(unique = true)
    @NotNull
    private String name;

    @Field("default_allow")
    private boolean defaultAllow = false;

    public Permission() {

    }

    public Permission(String name) {
        this.name = name;
    }

    public Permission(String name, boolean defaultAllow) {
        this.name = name;
        this.defaultAllow = defaultAllow;
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

    public boolean isDefaultAllow() {
        return defaultAllow;
    }

    public void setDefaultAllow(boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return Objects.equals(id, that.id) &&
            Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Permission{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", defaultAllow=" + defaultAllow +
            '}';
    }
}
