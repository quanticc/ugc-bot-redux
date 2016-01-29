package com.ugcleague.ops.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DiscordMessage.
 */
@Entity
@DynamicUpdate
@Table(name = "discord_message")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DiscordMessage extends AbstractAuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "discord_message_id", nullable = false, unique = true)
    private String discordMessageId;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "timestamp")
    private ZonedDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private DiscordUser author;

    @ManyToOne
    @JoinColumn(name = "channel_id")
    private DiscordChannel channel;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "owner")
    private Set<DiscordAttachment> attachments = new HashSet<>();

    @Column(name = "deleted")
    private Boolean deleted = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiscordMessageId() {
        return discordMessageId;
    }

    public void setDiscordMessageId(String discordMessageId) {
        this.discordMessageId = discordMessageId;
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

    public void setAuthor(DiscordUser discordUser) {
        this.author = discordUser;
    }

    public DiscordChannel getChannel() {
        return channel;
    }

    public void setChannel(DiscordChannel discordChannel) {
        this.channel = discordChannel;
    }

    public Set<DiscordAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(Set<DiscordAttachment> discordAttachments) {
        this.attachments = discordAttachments;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DiscordMessage discordMessage = (DiscordMessage) o;
        return Objects.equals(id, discordMessage.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DiscordMessage{" +
            "id=" + id +
            ", discordMessageId='" + discordMessageId + "'" +
            ", content='" + content + "'" +
            ", timestamp='" + timestamp + "'" +
            ", created=" + getCreatedDate() +
            ", lastModified=" + getLastModifiedDate() +
            ", deleted=" + deleted +
            '}';
    }
}
