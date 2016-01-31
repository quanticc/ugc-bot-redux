package com.ugcleague.ops.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "subscriber")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Subscriber extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "name")
    private String name;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "start")
    private ZonedDateTime start;

    @Column(name = "finish")
    private ZonedDateTime finish;

    @ManyToMany(mappedBy = "subscribers")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Publisher> subscriptions = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Publisher> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Set<Publisher> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public void setStart(ZonedDateTime start) {
        this.start = start;
    }

    public ZonedDateTime getFinish() {
        return finish;
    }

    public void setFinish(ZonedDateTime finish) {
        this.finish = finish;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subscriber that = (Subscriber) o;
        return Objects.equals(id, that.id) &&
            Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return "Subscriber{" +
            "id=" + id +
            "name='" + name + '\'' +
            ", userId='" + userId + '\'' +
            ", enabled=" + enabled +
            ", start=" + start +
            ", finish=" + finish +
            ", created_date=" + getCreatedDate() +
            ", last_modified_date=" + getLastModifiedDate() +
            '}';
    }
}
