package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.ZonedDateTime;
import java.util.Objects;

@Document(collection = "game_server")
public class GameServer {

    @Id
    private String id;

    private String address;

    private String name;

    private Integer ping;

    private Integer players;

    @Field("max_players")
    private Integer maxPlayers;

    @Field("map_name")
    private String mapName;

    @Field("expire_date")
    private ZonedDateTime expireDate;

    private Integer version;

    @Field("rcon_password")
    private String rconPassword;

    @Field("sv_password")
    private String svPassword;

    @Field("tv_port")
    private Integer tvPort;

    @Field("status_check_date")
    private ZonedDateTime statusCheckDate;

    @Field("last_valid_ping")
    private ZonedDateTime lastValidPing;

    @Field("expire_check_date")
    private ZonedDateTime expireCheckDate;

    @Field("last_rcon_date")
    private ZonedDateTime lastRconDate;

    @Field("last_game_update")
    private ZonedDateTime lastGameUpdate;

    private boolean claimable = true;

    private boolean secure = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public ZonedDateTime getLastValidPing() {
        return lastValidPing;
    }

    public void setLastValidPing(ZonedDateTime lastValidPing) {
        this.lastValidPing = lastValidPing;
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

    public boolean isClaimable() {
        return claimable;
    }

    public void setClaimable(boolean claimable) {
        this.claimable = claimable;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameServer that = (GameServer) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String getShortNameAndAddress() {
        return getShortName() + "(" + address + ")";
    }

    public String getShortName() {
        return name.trim().replaceAll("(^[A-Za-z]{3})[^0-9]*([0-9]+).*", "$1$2").toLowerCase();
    }

    @Override
    public String toString() {
        return getShortNameAndAddress();
    }
}
