package com.ugcleague.ops.domain.document;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.ZonedDateTime;
import java.util.Objects;

@Document(collection = "discord_message")
public class DiscordMessage extends AbstractAuditingEntity {

    @Id
    private String id;
    private String content;
    private ZonedDateTime timestamp;
    @DBRef
    private DiscordUser author;
    @DBRef
    private DiscordChannel channel;
    private boolean deleted;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public DiscordUser getAuthor() {
        return author;
    }

    public void setAuthor(DiscordUser author) {
        this.author = author;
    }

    public DiscordChannel getChannel() {
        return channel;
    }

    public void setChannel(DiscordChannel channel) {
        this.channel = channel;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscordMessage that = (DiscordMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiscordMessage{" +
            "id='" + id + '\'' +
            ", content='" + content + '\'' +
            ", timestamp=" + timestamp +
            ", author=" + (author != null ? author.getId() : "null") +
            ", channel=" + (channel != null ? channel.getId() : "null") +
            ", deleted=" + deleted +
            //", events=" + events +
            '}';
    }
}
