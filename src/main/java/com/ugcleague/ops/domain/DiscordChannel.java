package com.ugcleague.ops.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DiscordChannel.
 */
@Entity
@Table(name = "discord_channel")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DiscordChannel extends AbstractAuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "discord_channel_id", nullable = false, unique = true)
    private String discordChannelId;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @Column(name = "parent_guild_id")
    private String parentGuildId;

    @OneToMany(mappedBy = "channel")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DiscordMessage> messages = new HashSet<>();

    @Column(name = "name")
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiscordChannelId() {
        return discordChannelId;
    }

    public void setDiscordChannelId(String discordChannelId) {
        this.discordChannelId = discordChannelId;
    }

    public Boolean getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public String getParentGuildId() {
        return parentGuildId;
    }

    public void setParentGuildId(String parentGuildId) {
        this.parentGuildId = parentGuildId;
    }

    public Set<DiscordMessage> getMessages() {
        return messages;
    }

    public void setMessages(Set<DiscordMessage> discordMessages) {
        this.messages = discordMessages;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DiscordChannel discordChannel = (DiscordChannel) o;
        return Objects.equals(id, discordChannel.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DiscordChannel{" +
            "id=" + id +
            ", name='" + name + "'" +
            ", discordChannelId='" + discordChannelId + "'" +
            ", isPrivate='" + isPrivate + "'" +
            ", parentGuildId='" + parentGuildId + "'" +
            '}';
    }
}
