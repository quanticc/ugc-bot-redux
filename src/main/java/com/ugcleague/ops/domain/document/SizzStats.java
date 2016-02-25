package com.ugcleague.ops.domain.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.ZonedDateTime;
import java.util.*;

@Document(collection = "sizz_stats")
public class SizzStats {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Field("blu_country")
    @JsonProperty("bluCountry")
    private String bluCountry;

    @Field("red_country")
    @JsonProperty("redCountry")
    private String redCountry;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("map")
    private String map;

    @Field("blu_name")
    @JsonProperty("bluname")
    private String bluName;

    @JsonProperty("redname")
    private String redName;

    @Field("host_ip")
    @JsonProperty("hostip")
    private String hostIp;

    @Indexed
    private String ip;

    @Field("host_port")
    @JsonProperty("hostport")
    private Long hostPort;

    private Long round;
    private ZonedDateTime updated;
    private ZonedDateTime created;

    @Field("view_count")
    @JsonProperty("viewCount")
    private Long viewCount;

    @Field("plugin_version")
    @JsonProperty("pluginVersion")
    private String pluginVersion;

    @Field("__v")
    @JsonProperty("__v")
    private Long version;

    @Field("match_duration")
    @JsonProperty("matchDuration")
    private Long matchDuration;

    @Field("is_live")
    @JsonProperty("isLive")
    private boolean isLive;

    private Owner owner;

    @JsonProperty("chats")
    private List<Object> chats = new ArrayList<>();

    @JsonProperty("players")
    private Map<String, Player> players = new LinkedHashMap<>();

    @Field("round_duration")
    @JsonProperty("roundduration")
    private List<Long> roundDuration = new ArrayList<>();

    @Field("team_first_cap")
    @JsonProperty("teamfirstcap")
    private List<Long> teamFirstCap = new ArrayList<>();

    @Field("blu_score")
    @JsonProperty("bluscore")
    private List<Long> bluScore = new ArrayList<>();

    @Field("red_score")
    @JsonProperty("redscore")
    private List<Long> redScore = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBluCountry() {
        return bluCountry;
    }

    public void setBluCountry(String bluCountry) {
        this.bluCountry = bluCountry;
    }

    public String getRedCountry() {
        return redCountry;
    }

    public void setRedCountry(String redCountry) {
        this.redCountry = redCountry;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public String getBluName() {
        return bluName;
    }

    public void setBluName(String bluName) {
        this.bluName = bluName;
    }

    public String getRedName() {
        return redName;
    }

    public void setRedName(String redName) {
        this.redName = redName;
    }

    public String getHostIp() {
        return hostIp;
    }

    public void setHostIp(String hostIp) {
        this.hostIp = hostIp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Long getHostPort() {
        return hostPort;
    }

    public void setHostPort(Long hostPort) {
        this.hostPort = hostPort;
    }

    public Long getRound() {
        return round;
    }

    public void setRound(Long round) {
        this.round = round;
    }

    public ZonedDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(ZonedDateTime updated) {
        this.updated = updated;
    }

    public ZonedDateTime getCreated() {
        return created;
    }

    public void setCreated(ZonedDateTime created) {
        this.created = created;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getMatchDuration() {
        return matchDuration;
    }

    public void setMatchDuration(Long matchDuration) {
        this.matchDuration = matchDuration;
    }

    public boolean isLive() {
        return isLive;
    }

    public void setLive(boolean live) {
        isLive = live;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public List<Object> getChats() {
        return chats;
    }

    public void setChats(List<Object> chats) {
        this.chats = chats;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public void setPlayers(Map<String, Player> players) {
        this.players = players;
    }

    public List<Long> getRoundDuration() {
        return roundDuration;
    }

    public void setRoundDuration(List<Long> roundDuration) {
        this.roundDuration = roundDuration;
    }

    public List<Long> getTeamFirstCap() {
        return teamFirstCap;
    }

    public void setTeamFirstCap(List<Long> teamFirstCap) {
        this.teamFirstCap = teamFirstCap;
    }

    public List<Long> getBluScore() {
        return bluScore;
    }

    public void setBluScore(List<Long> bluScore) {
        this.bluScore = bluScore;
    }

    public List<Long> getRedScore() {
        return redScore;
    }

    public void setRedScore(List<Long> redScore) {
        this.redScore = redScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SizzStats sizzStats = (SizzStats) o;
        return Objects.equals(id, sizzStats.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static class Owner {

        @JsonProperty("name")
        private String name;

        @Field("numeric_id")
        @Indexed
        @JsonProperty("numericid")
        private String numericid;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNumericid() {
            return numericid;
        }

        public void setNumericid(String numericid) {
            this.numericid = numericid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Owner owner = (Owner) o;
            return Objects.equals(numericid, owner.numericid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numericid);
        }
    }

    public static class Player {

        @Id
        @JsonProperty("_id")
        private String id;

        private String name;

        @Field("steam_id")
        @JsonProperty("steamid")
        private String steamId;

        private Long team;

        @Field("med_picks")
        @JsonProperty("medpicks")
        private List<Long> medPicks = new ArrayList<>();

        @Field("ubers_dropped")
        @JsonProperty("ubersdropped")
        private List<Long> ubersDropped = new ArrayList<>();

        @Field("heals_received")
        @JsonProperty("healsreceived")
        private List<Long> healsReceived = new ArrayList<>();

        private List<Long> points = new ArrayList<>();

        @Field("bonus_points")
        @JsonProperty("bonuspoints")
        private List<Long> bonusPoints = new ArrayList<>();

        @Field("resupply_points")
        @JsonProperty("resupplypoints")
        private List<Long> resupplyPoints = new ArrayList<>();

        private List<Long> crits = new ArrayList<>();

        @Field("overkill_damage")
        @JsonProperty("overkillDamage")
        private List<Object> overkillDamage = new ArrayList<>();

        @Field("damage_done")
        @JsonProperty("damagedone")
        private List<Long> damageDone = new ArrayList<>();

        private List<Long> teleports = new ArrayList<>();
        private List<Long> invulns = new ArrayList<>();
        private List<Long> healpoints = new ArrayList<>();
        private List<Long> backstabs = new ArrayList<>();
        private List<Long> headshots = new ArrayList<>();

        @Field("buildings_destroyed")
        @JsonProperty("buildingsdestroyed")
        private List<Long> buildingsDestroyed = new ArrayList<>();

        @Field("buildings_built")
        @JsonProperty("buildingsbuilt")
        private List<Long> buildingsBuilt = new ArrayList<>();

        private List<Long> revenge = new ArrayList<>();
        private List<Long> dominations = new ArrayList<>();
        private List<Long> suicides = new ArrayList<>();
        private List<Long> defenses = new ArrayList<>();
        private List<Long> captures = new ArrayList<>();
        private List<Long> deaths = new ArrayList<>();

        @Field("kill_assists")
        @JsonProperty("killassists")
        private List<Long> killAssists = new ArrayList<>();

        private List<Long> kills = new ArrayList<>();

        @Field("played_classes")
        @JsonProperty("playedclasses")
        private List<Long> playedClasses = new ArrayList<>();

        @Field("most_played_class")
        @JsonProperty("mostplayedclass")
        private List<Long> mostPlayedClass = new ArrayList<>();

        private String avatar;

        @Field("numeric_id")
        @JsonProperty("numericid")
        private String numericId;

        private String country;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSteamId() {
            return steamId;
        }

        public void setSteamId(String steamId) {
            this.steamId = steamId;
        }

        public Long getTeam() {
            return team;
        }

        public void setTeam(Long team) {
            this.team = team;
        }

        public List<Long> getMedPicks() {
            return medPicks;
        }

        public void setMedPicks(List<Long> medPicks) {
            this.medPicks = medPicks;
        }

        public List<Long> getUbersDropped() {
            return ubersDropped;
        }

        public void setUbersDropped(List<Long> ubersDropped) {
            this.ubersDropped = ubersDropped;
        }

        public List<Long> getHealsReceived() {
            return healsReceived;
        }

        public void setHealsReceived(List<Long> healsReceived) {
            this.healsReceived = healsReceived;
        }

        public List<Long> getPoints() {
            return points;
        }

        public void setPoints(List<Long> points) {
            this.points = points;
        }

        public List<Long> getBonusPoints() {
            return bonusPoints;
        }

        public void setBonusPoints(List<Long> bonusPoints) {
            this.bonusPoints = bonusPoints;
        }

        public List<Long> getResupplyPoints() {
            return resupplyPoints;
        }

        public void setResupplyPoints(List<Long> resupplyPoints) {
            this.resupplyPoints = resupplyPoints;
        }

        public List<Long> getCrits() {
            return crits;
        }

        public void setCrits(List<Long> crits) {
            this.crits = crits;
        }

        public List<Object> getOverkillDamage() {
            return overkillDamage;
        }

        public void setOverkillDamage(List<Object> overkillDamage) {
            this.overkillDamage = overkillDamage;
        }

        public List<Long> getDamageDone() {
            return damageDone;
        }

        public void setDamageDone(List<Long> damageDone) {
            this.damageDone = damageDone;
        }

        public List<Long> getTeleports() {
            return teleports;
        }

        public void setTeleports(List<Long> teleports) {
            this.teleports = teleports;
        }

        public List<Long> getInvulns() {
            return invulns;
        }

        public void setInvulns(List<Long> invulns) {
            this.invulns = invulns;
        }

        public List<Long> getHealpoints() {
            return healpoints;
        }

        public void setHealpoints(List<Long> healpoints) {
            this.healpoints = healpoints;
        }

        public List<Long> getBackstabs() {
            return backstabs;
        }

        public void setBackstabs(List<Long> backstabs) {
            this.backstabs = backstabs;
        }

        public List<Long> getHeadshots() {
            return headshots;
        }

        public void setHeadshots(List<Long> headshots) {
            this.headshots = headshots;
        }

        public List<Long> getBuildingsDestroyed() {
            return buildingsDestroyed;
        }

        public void setBuildingsDestroyed(List<Long> buildingsDestroyed) {
            this.buildingsDestroyed = buildingsDestroyed;
        }

        public List<Long> getBuildingsBuilt() {
            return buildingsBuilt;
        }

        public void setBuildingsBuilt(List<Long> buildingsBuilt) {
            this.buildingsBuilt = buildingsBuilt;
        }

        public List<Long> getRevenge() {
            return revenge;
        }

        public void setRevenge(List<Long> revenge) {
            this.revenge = revenge;
        }

        public List<Long> getDominations() {
            return dominations;
        }

        public void setDominations(List<Long> dominations) {
            this.dominations = dominations;
        }

        public List<Long> getSuicides() {
            return suicides;
        }

        public void setSuicides(List<Long> suicides) {
            this.suicides = suicides;
        }

        public List<Long> getDefenses() {
            return defenses;
        }

        public void setDefenses(List<Long> defenses) {
            this.defenses = defenses;
        }

        public List<Long> getCaptures() {
            return captures;
        }

        public void setCaptures(List<Long> captures) {
            this.captures = captures;
        }

        public List<Long> getDeaths() {
            return deaths;
        }

        public void setDeaths(List<Long> deaths) {
            this.deaths = deaths;
        }

        public List<Long> getKillAssists() {
            return killAssists;
        }

        public void setKillAssists(List<Long> killAssists) {
            this.killAssists = killAssists;
        }

        public List<Long> getKills() {
            return kills;
        }

        public void setKills(List<Long> kills) {
            this.kills = kills;
        }

        public List<Long> getPlayedClasses() {
            return playedClasses;
        }

        public void setPlayedClasses(List<Long> playedClasses) {
            this.playedClasses = playedClasses;
        }

        public List<Long> getMostPlayedClass() {
            return mostPlayedClass;
        }

        public void setMostPlayedClass(List<Long> mostPlayedClass) {
            this.mostPlayedClass = mostPlayedClass;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public String getNumericId() {
            return numericId;
        }

        public void setNumericId(String numericId) {
            this.numericId = numericId;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Player that = (Player) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
