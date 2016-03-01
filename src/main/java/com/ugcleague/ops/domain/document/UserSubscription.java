package com.ugcleague.ops.domain.document;

import org.springframework.data.mongodb.core.mapping.DBRef;

public class UserSubscription extends Subscription {

    @DBRef
    private DiscordUser user;

    public DiscordUser getUser() {
        return user;
    }

    public void setUser(DiscordUser user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "Subscription{" +
            "user=" + user +
            ", enabled=" + isEnabled() +
            ", mode=" + getMode() +
            ", start=" + getStart() +
            ", finish=" + getFinish() +
            ", createdDate=" + getCreatedDate() +
            ", lastModifiedDate=" + getLastModifiedDate() +
            '}';
    }
}
