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
 * A GameServer.
 */
@Entity
@DynamicUpdate
@Table(name = "game_server")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@NamedEntityGraphs(@NamedEntityGraph(name = "GameServer.detail", attributeNodes = {@NamedAttributeNode("flags")}))
public class GameServer implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotNull
    @Column(name = "address", nullable = false, unique = true)
    private String address;

    @Column(name = "name")
    private String name;

    @NotNull
    @Column(name = "sub_id", nullable = false, unique = true)
    private String subId;

    @Column(name = "ping")
    private Integer ping;

    @Column(name = "players")
    private Integer players;

    @Column(name = "max_players")
    private Integer maxPlayers;

    @Column(name = "map_name")
    private String mapName;

    @Column(name = "expire_date")
    private ZonedDateTime expireDate;

    @Column(name = "version")
    private Integer version;

    @Column(name = "rcon_password")
    private String rconPassword;

    @Column(name = "sv_password")
    private String svPassword;

    @Column(name = "tv_port")
    private Integer tvPort;

    @Column(name = "status_check_date")
    private ZonedDateTime statusCheckDate;

    @Column(name = "expire_check_date")
    private ZonedDateTime expireCheckDate;

    @Column(name = "last_rcon_date")
    private ZonedDateTime lastRconDate;

    @Column(name = "last_game_update")
    private ZonedDateTime lastGameUpdate;

    @ManyToMany
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JoinTable(name = "game_server_flag", joinColumns = @JoinColumn(name = "game_servers_id", referencedColumnName = "ID"), inverseJoinColumns = @JoinColumn(name = "flags_id", referencedColumnName = "ID"))
    private Set<Flag> flags = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubId() {
        return subId;
    }

    public void setSubId(String subId) {
        this.subId = subId;
    }

    public Integer getPing() {
        return ping;
    }

    public void setPing(Integer ping) {
        this.ping = ping;
    }

    public Integer getPlayers() {
        return players;
    }

    public void setPlayers(Integer players) {
        this.players = players;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public ZonedDateTime getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(ZonedDateTime expireDate) {
        this.expireDate = expireDate;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getRconPassword() {
        return rconPassword;
    }

    public void setRconPassword(String rconPassword) {
        this.rconPassword = rconPassword;
    }

    public String getSvPassword() {
        return svPassword;
    }

    public void setSvPassword(String svPassword) {
        this.svPassword = svPassword;
    }

    public Integer getTvPort() {
        return tvPort;
    }

    public void setTvPort(Integer tvPort) {
        this.tvPort = tvPort;
    }

    public ZonedDateTime getStatusCheckDate() {
        return statusCheckDate;
    }

    public void setStatusCheckDate(ZonedDateTime statusCheckDate) {
        this.statusCheckDate = statusCheckDate;
    }

    public ZonedDateTime getExpireCheckDate() {
        return expireCheckDate;
    }

    public void setExpireCheckDate(ZonedDateTime expireCheckDate) {
        this.expireCheckDate = expireCheckDate;
    }

    public ZonedDateTime getLastRconDate() {
        return lastRconDate;
    }

    public void setLastRconDate(ZonedDateTime lastRconDate) {
        this.lastRconDate = lastRconDate;
    }

    public ZonedDateTime getLastGameUpdate() {
        return lastGameUpdate;
    }

    public void setLastGameUpdate(ZonedDateTime lastGameUpdate) {
        this.lastGameUpdate = lastGameUpdate;
    }

    public Set<Flag> getFlags() {
        return flags;
    }

    public void setFlags(Set<Flag> flags) {
        this.flags = flags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GameServer gameServer = (GameServer) o;
        return Objects.equals(id, gameServer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "GameServer{" + "id=" + id + ", address='" + address + "'" + ", name='" + name + "'" + ", subId='" + subId + "'"
            + ", ping='" + ping + "'" + ", players='" + players + "'" + ", maxPlayers='" + maxPlayers + "'" + ", mapName='"
            + mapName + "'" + ", expireDate='" + expireDate + "'" + ", version='" + version + "'" + ", rconPassword='"
            + rconPassword + "'" + ", svPassword='" + svPassword + "'" + ", tvPort='" + tvPort + "'" + ", statusCheckDate='"
            + statusCheckDate + "'" + ", expireCheckDate='" + expireCheckDate + "'" + ", lastRconDate='" + lastRconDate + "'"
            + ", lastGameUpdate='" + lastGameUpdate + "'" + '}';
    }

    public String getShortName() {
        return name.trim().replaceAll("(^[A-Za-z]{3})[^0-9]*([0-9]+).*", "$1$2").toLowerCase();
    }
}
