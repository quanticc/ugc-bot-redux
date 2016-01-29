package com.ugcleague.ops.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DiscordUser.
 */
@Entity
@Table(name = "discord_user")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DiscordUser implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "discord_user_id", nullable = false, unique = true)
    private String discordUserId;

    @Column(name = "name")
    private String name;

    @Column(name = "joined")
    private ZonedDateTime joined = ZonedDateTime.now();

    @Column(name = "connected")
    private ZonedDateTime connected = ZonedDateTime.now();

    @Column(name = "disconnected")
    private ZonedDateTime disconnected = ZonedDateTime.now();

    @OneToMany(mappedBy = "author")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DiscordMessage> messages = new HashSet<>();

    @Column(name = "total_uptime")
    private Long totalUptime = 0L;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

    public void setDiscordUserId(String discordUserId) {
        this.discordUserId = discordUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ZonedDateTime getJoined() {
        return joined;
    }

    public void setJoined(ZonedDateTime joined) {
        this.joined = joined;
    }

    public ZonedDateTime getConnected() {
        return connected;
    }

    public void setConnected(ZonedDateTime connected) {
        this.connected = connected;
    }

    public ZonedDateTime getDisconnected() {
        return disconnected;
    }

    public void setDisconnected(ZonedDateTime disconnected) {
        this.disconnected = disconnected;
    }

    public Set<DiscordMessage> getMessages() {
        return messages;
    }

    public void setMessages(Set<DiscordMessage> discordMessages) {
        this.messages = discordMessages;
    }

    public Long getTotalUptime() {
        return totalUptime;
    }

    public void setTotalUptime(Long totalUptime) {
        this.totalUptime = totalUptime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DiscordUser discordUser = (DiscordUser) o;
        return Objects.equals(id, discordUser.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DiscordUser{" +
            "id=" + id +
            ", discordUserId='" + discordUserId + "'" +
            ", name='" + name + "'" +
            ", joined='" + joined + "'" +
            ", connected='" + connected + "'" +
            ", disconnected='" + disconnected + "'" +
            ", totalUptime=" + totalUptime +
            '}';
    }
}
