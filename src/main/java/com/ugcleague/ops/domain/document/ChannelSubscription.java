package com.ugcleague.ops.domain.document;

import org.springframework.data.mongodb.core.mapping.DBRef;

public class ChannelSubscription extends Subscription {

    @DBRef
    private DiscordChannel channel;

    public DiscordChannel getChannel() {
        return channel;
    }

    public void setChannel(DiscordChannel channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return "Subscription{" +
            "channel=" + channel +
            ", enabled=" + isEnabled() +
            ", mode=" + getMode() +
            ", start=" + getStart() +
            ", finish=" + getFinish() +
            ", createdDate=" + getCreatedDate() +
            ", lastModifiedDate=" + getLastModifiedDate() +
            '}';
    }
}
