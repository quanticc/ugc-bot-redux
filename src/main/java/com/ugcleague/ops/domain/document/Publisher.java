package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Document(collection = "publisher")
public class Publisher extends AbstractAuditingEntity {

    @Id
    private String id;

    @Indexed(sparse = true)
    private String channelId;

    private boolean enabled = true;

    private Set<UserSubscription> userSubscriptions = new LinkedHashSet<>();

    private Set<ChannelSubscription> channelSubscriptions = new LinkedHashSet<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<UserSubscription> getUserSubscriptions() {
        return userSubscriptions;
    }

    public void setUserSubscriptions(Set<UserSubscription> userSubscriptions) {
        this.userSubscriptions = userSubscriptions;
    }

    public Set<ChannelSubscription> getChannelSubscriptions() {
        return channelSubscriptions;
    }

    public void setChannelSubscriptions(Set<ChannelSubscription> channelSubscriptions) {
        this.channelSubscriptions = channelSubscriptions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Publisher publisher = (Publisher) o;
        return Objects.equals(id, publisher.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Publisher{" +
            "id='" + id + '\'' +
            ", channelId='" + channelId + '\'' +
            ", enabled=" + enabled +
            ", userSubscriptions=" + userSubscriptions +
            ", channelSubscriptions=" + channelSubscriptions +
            '}';
    }
}
