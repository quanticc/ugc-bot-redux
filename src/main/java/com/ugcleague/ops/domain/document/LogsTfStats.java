package com.ugcleague.ops.domain.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.*;

@Document(collection = "logstf_stats")
public class LogsTfStats {

    @Id
    @JsonIgnore
    private Long id;

    private Integer version;
    private Map<String, Team> teams = new LinkedHashMap<>();
    private Long length;
    private Map<String, Player> players = new LinkedHashMap<>();
    private Map<String, String> names = new LinkedHashMap<>();
    private List<Round> rounds = new ArrayList<>();

    /**
     * Map of source steam_id (could be steam3 or steamId32) to target steam_id to heal amount.
     */
    private Map<String, Map<String, Long>> healspread = new LinkedHashMap<>();

    /**
     * Map of source steam_id (could be steam3 or steamId32) to target class to kill amount.
     */
    @Field("class_kills")
    @JsonProperty("classkills")
    private Map<String, Map<String, Long>> classKills = new LinkedHashMap<>();

    /**
     * Map of source steam_id (could be steam3 or steamId32) to target class to death amount.
     */
    @Field("class_deaths")
    @JsonProperty("classdeaths")
    private Map<String, Map<String, Long>> classDeaths = new LinkedHashMap<>();

    /**
     * Since version 3
     */
    @Field("class_killassists")
    @JsonProperty("classkillassists")
    private Map<String, Map<String, Long>> classKillAssists = new LinkedHashMap<>();

    private List<Chat> chat = new ArrayList<>();
    private Info info = new Info();

    @Field("kill_streaks")
    @JsonProperty("killstreaks")
    private List<KillStreak> killStreaks = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Map<String, Team> getTeams() {
        return teams;
    }

    public void setTeams(Map<String, Team> teams) {
        this.teams = teams;
    }

    public Long getLength() {
        return length;
    }

    public void setLength(Long length) {
        this.length = length;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public void setPlayers(Map<String, Player> players) {
        this.players = players;
    }

    public Map<String, String> getNames() {
        return names;
    }

    public void setNames(Map<String, String> names) {
        this.names = names;
    }

    public List<Round> getRounds() {
        return rounds;
    }

    public void setRounds(List<Round> rounds) {
        this.rounds = rounds;
    }

    public Map<String, Map<String, Long>> getHealspread() {
        return healspread;
    }

    public void setHealspread(Map<String, Map<String, Long>> healspread) {
        this.healspread = healspread;
    }

    public Map<String, Map<String, Long>> getClassKills() {
        return classKills;
    }

    public void setClassKills(Map<String, Map<String, Long>> classKills) {
        this.classKills = classKills;
    }

    public Map<String, Map<String, Long>> getClassDeaths() {
        return classDeaths;
    }

    public void setClassDeaths(Map<String, Map<String, Long>> classDeaths) {
        this.classDeaths = classDeaths;
    }

    public List<Chat> getChat() {
        return chat;
    }

    public void setChat(List<Chat> chat) {
        this.chat = chat;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public List<KillStreak> getKillStreaks() {
        return killStreaks;
    }

    public void setKillStreaks(List<KillStreak> killStreaks) {
        this.killStreaks = killStreaks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogsTfStats that = (LogsTfStats) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Map<String, Map<String, Long>> getClassKillAssists() {
        return classKillAssists;
    }

    public void setClassKillAssists(Map<String, Map<String, Long>> classKillAssists) {
        this.classKillAssists = classKillAssists;
    }

    public static class Team {

        private Long score;
        private Long kills;
        private Long deaths;
        private Long dmg;
        private Long charges;

        @Field("first_caps")
        @JsonProperty("firstcaps")
        private Long firstCaps;

        private Long caps;

        public Long getScore() {
            return score;
        }

        public void setScore(Long score) {
            this.score = score;
        }

        public Long getKills() {
            return kills;
        }

        public void setKills(Long kills) {
            this.kills = kills;
        }

        public Long getDeaths() {
            return deaths;
        }

        public void setDeaths(Long deaths) {
            this.deaths = deaths;
        }

        public Long getDmg() {
            return dmg;
        }

        public void setDmg(Long dmg) {
            this.dmg = dmg;
        }

        public Long getCharges() {
            return charges;
        }

        public void setCharges(Long charges) {
            this.charges = charges;
        }

        public Long getFirstCaps() {
            return firstCaps;
        }

        public void setFirstCaps(Long firstCaps) {
            this.firstCaps = firstCaps;
        }

        public Long getCaps() {
            return caps;
        }

        public void setCaps(Long caps) {
            this.caps = caps;
        }
    }

    /**
     * Individual player stats. New fields from v3:
     * <ul>
     * <li>suicides</li>
     * <li>dmg_real</li>
     * <li>dt</li>
     * <li>dt_real</li>
     * <li>hr</li>
     * <li>as</li>
     * <li>medkits_hp</li>
     * <li>headshots_hit</li>
     * <li>medicstats</li>
     * </ul>
     */
    public static class Player {

        private String team;
        private Long kills;
        private Long deaths;
        private Long assists;
        private Long suicides;
        private String kapd;
        private String kpd;
        private Long dmg;

        @Field("dmg_real")
        @JsonProperty("dmg_real")
        private Long dmgReal;

        private Long dt;

        @Field("dt_real")
        @JsonProperty("dt_real")
        private Long dtReal;

        private Long hr;
        private Long lks;
        private Long as;
        private Long dapd;
        private Long dapm;
        private Long ubers;
        private Long drops;
        private Long medkits;
        @Field("medkits_hp")
        @JsonProperty("medkits_hp")
        private Long medkitsHp;
        private Long backstabs;
        private Long headshots;
        @Field("headshots_hit")
        @JsonProperty("headshots_hit")
        private Long headshotsHit;
        private Long sentries;
        private Long heal;
        private Long cpc;
        private Long ic;

        @Field("class_stats")
        @JsonProperty("class_stats")
        private List<ClassStats> classStats = new ArrayList<>();

        @Field("medic_stats")
        @JsonProperty("medicstats")
        private Map<String, Object> medicStats = new LinkedHashMap<>();

        public String getTeam() {
            return team;
        }

        public void setTeam(String team) {
            this.team = team;
        }

        public Long getKills() {
            return kills;
        }

        public void setKills(Long kills) {
            this.kills = kills;
        }

        public Long getDeaths() {
            return deaths;
        }

        public void setDeaths(Long deaths) {
            this.deaths = deaths;
        }

        public Long getAssists() {
            return assists;
        }

        public void setAssists(Long assists) {
            this.assists = assists;
        }

        public String getKapd() {
            return kapd;
        }

        public void setKapd(String kapd) {
            this.kapd = kapd;
        }

        public String getKpd() {
            return kpd;
        }

        public void setKpd(String kpd) {
            this.kpd = kpd;
        }

        public Long getDmg() {
            return dmg;
        }

        public void setDmg(Long dmg) {
            this.dmg = dmg;
        }

        public Long getLks() {
            return lks;
        }

        public void setLks(Long lks) {
            this.lks = lks;
        }

        public Long getDapd() {
            return dapd;
        }

        public void setDapd(Long dapd) {
            this.dapd = dapd;
        }

        public Long getDapm() {
            return dapm;
        }

        public void setDapm(Long dapm) {
            this.dapm = dapm;
        }

        public Long getUbers() {
            return ubers;
        }

        public void setUbers(Long ubers) {
            this.ubers = ubers;
        }

        public Long getDrops() {
            return drops;
        }

        public void setDrops(Long drops) {
            this.drops = drops;
        }

        public Long getBackstabs() {
            return backstabs;
        }

        public void setBackstabs(Long backstabs) {
            this.backstabs = backstabs;
        }

        public Long getHeadshots() {
            return headshots;
        }

        public void setHeadshots(Long headshots) {
            this.headshots = headshots;
        }

        public Long getSentries() {
            return sentries;
        }

        public void setSentries(Long sentries) {
            this.sentries = sentries;
        }

        public Long getHeal() {
            return heal;
        }

        public void setHeal(Long heal) {
            this.heal = heal;
        }

        public Long getCpc() {
            return cpc;
        }

        public void setCpc(Long cpc) {
            this.cpc = cpc;
        }

        public Long getIc() {
            return ic;
        }

        public void setIc(Long ic) {
            this.ic = ic;
        }

        public Long getMedkits() {
            return medkits;
        }

        public void setMedkits(Long medkits) {
            this.medkits = medkits;
        }

        public List<ClassStats> getClassStats() {
            return classStats;
        }

        public void setClassStats(List<ClassStats> classStats) {
            this.classStats = classStats;
        }

        public Long getSuicides() {
            return suicides;
        }

        public void setSuicides(Long suicides) {
            this.suicides = suicides;
        }

        public Long getDmgReal() {
            return dmgReal;
        }

        public void setDmgReal(Long dmgReal) {
            this.dmgReal = dmgReal;
        }

        public Long getDt() {
            return dt;
        }

        public void setDt(Long dt) {
            this.dt = dt;
        }

        public Long getDtReal() {
            return dtReal;
        }

        public void setDtReal(Long dtReal) {
            this.dtReal = dtReal;
        }

        public Long getHr() {
            return hr;
        }

        public void setHr(Long hr) {
            this.hr = hr;
        }

        public Long getAs() {
            return as;
        }

        public void setAs(Long as) {
            this.as = as;
        }

        public Long getMedkitsHp() {
            return medkitsHp;
        }

        public void setMedkitsHp(Long medkitsHp) {
            this.medkitsHp = medkitsHp;
        }

        public Long getHeadshotsHit() {
            return headshotsHit;
        }

        public void setHeadshotsHit(Long headshotsHit) {
            this.headshotsHit = headshotsHit;
        }

        public Map<String, Object> getMedicStats() {
            return medicStats;
        }

        public void setMedicStats(Map<String, Object> medicStats) {
            this.medicStats = medicStats;
        }
    }

    public static class ClassStats {

        private Long kills;
        private Long assists;
        private Long deaths;
        private Long dmg;

        /**
         * In older logs, this field signalled a numeric value with a weapon name. But in version 3, this field now
         * keeps track of various weapon stats. Therefore, since Jackson does not have versioning support, the
         * deserialization will be made into an object.
         */
        private Map<String, Object> weapon = new LinkedHashMap<>();

        @Field("total_time")
        @JsonProperty("total_time")
        private Long totalTime;

        private String type;

        public Long getKills() {
            return kills;
        }

        public void setKills(Long kills) {
            this.kills = kills;
        }

        public Long getAssists() {
            return assists;
        }

        public void setAssists(Long assists) {
            this.assists = assists;
        }

        public Long getDeaths() {
            return deaths;
        }

        public void setDeaths(Long deaths) {
            this.deaths = deaths;
        }

        public Long getDmg() {
            return dmg;
        }

        public void setDmg(Long dmg) {
            this.dmg = dmg;
        }

        public Map<String, Object> getWeapon() {
            return weapon;
        }

        public void setWeapon(Map<String, Object> weapon) {
            this.weapon = weapon;
        }

        public Long getTotalTime() {
            return totalTime;
        }

        public void setTotalTime(Long totalTime) {
            this.totalTime = totalTime;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Round {

        /**
         * In Epoch Seconds
         */
        @Field("start_time")
        @JsonProperty("start_time")
        private Long startTime;

        private String winner;
        private Team team = new Team();
        private List<Map<String, Object>> events = new ArrayList<>();
        private Map<String, PlayerRound> players = new LinkedHashMap<>();
        private String firstcap;
        /**
         * In seconds
         */
        private Long length;

        public Long getStartTime() {
            return startTime;
        }

        public void setStartTime(Long startTime) {
            this.startTime = startTime;
        }

        public String getWinner() {
            return winner;
        }

        public void setWinner(String winner) {
            this.winner = winner;
        }

        public Team getTeam() {
            return team;
        }

        public void setTeam(Team team) {
            this.team = team;
        }

        public List<Map<String, Object>> getEvents() {
            return events;
        }

        public void setEvents(List<Map<String, Object>> events) {
            this.events = events;
        }

        public Map<String, PlayerRound> getPlayers() {
            return players;
        }

        public void setPlayers(Map<String, PlayerRound> players) {
            this.players = players;
        }

        public String getFirstcap() {
            return firstcap;
        }

        public void setFirstcap(String firstcap) {
            this.firstcap = firstcap;
        }

        public Long getLength() {
            return length;
        }

        public void setLength(Long length) {
            this.length = length;
        }
    }

    public static class PlayerRound {

        private Long kills;
        private Long dmg;

        public Long getKills() {
            return kills;
        }

        public void setKills(Long kills) {
            this.kills = kills;
        }

        public Long getDmg() {
            return dmg;
        }

        public void setDmg(Long dmg) {
            this.dmg = dmg;
        }
    }

    public static class Chat {

        @Field("steam_id")
        @JsonProperty("steamid")
        private String steamId;

        private String name;
        private String msg;

        public String getSteamId() {
            return steamId;
        }

        public void setSteamId(String steamId) {
            this.steamId = steamId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }
    }

    /**
     * Info about the stats plugin in the server. Since v2/v3
     * <ul>
     * <li>hasRealDamage</li>
     * <li>hasWeaponDamage</li>
     * <li>notifications</li>
     * </ul>
     */
    public static class Info {

        private String map;
        private Boolean supplemental;

        @Field("total_length")
        @JsonProperty("total_length")
        private Long totalLength;

        @Field("has_real_damage")
        @JsonProperty("hasRealDamage")
        private Boolean hasRealDamage;

        @Field("has_weapon_damage")
        @JsonProperty("hasWeaponDamage")
        private Boolean hasWeaponDamage;

        @Field("has_hp")
        @JsonProperty("hasHP")
        private Boolean hasHP;

        @Field("has_hs")
        @JsonProperty("hasHS")
        private Boolean hasHS;

        @Field("has_bs")
        @JsonProperty("hasBS")
        private Boolean hasBS;

        @Field("has_cp")
        @JsonProperty("hasCP")
        private Boolean hasCP;

        @Field("has_sb")
        @JsonProperty("hasSB")
        private Boolean hasSB;

        @Field("has_intel")
        @JsonProperty("hasIntel")
        private Boolean hasIntel;

        private List<Object> notifications = new ArrayList<>();

        public String getMap() {
            return map;
        }

        public void setMap(String map) {
            this.map = map;
        }

        public Boolean getSupplemental() {
            return supplemental;
        }

        public void setSupplemental(Boolean supplemental) {
            this.supplemental = supplemental;
        }

        public Long getTotalLength() {
            return totalLength;
        }

        public void setTotalLength(Long totalLength) {
            this.totalLength = totalLength;
        }

        public Boolean getHasHP() {
            return hasHP;
        }

        public void setHasHP(Boolean hasHP) {
            this.hasHP = hasHP;
        }

        public Boolean getHasHS() {
            return hasHS;
        }

        public void setHasHS(Boolean hasHS) {
            this.hasHS = hasHS;
        }

        public Boolean getHasBS() {
            return hasBS;
        }

        public void setHasBS(Boolean hasBS) {
            this.hasBS = hasBS;
        }

        public Boolean getHasCP() {
            return hasCP;
        }

        public void setHasCP(Boolean hasCP) {
            this.hasCP = hasCP;
        }

        public Boolean getHasSB() {
            return hasSB;
        }

        public void setHasSB(Boolean hasSB) {
            this.hasSB = hasSB;
        }

        public Boolean getHasIntel() {
            return hasIntel;
        }

        public void setHasIntel(Boolean hasIntel) {
            this.hasIntel = hasIntel;
        }

        public Boolean getHasRealDamage() {
            return hasRealDamage;
        }

        public void setHasRealDamage(Boolean hasRealDamage) {
            this.hasRealDamage = hasRealDamage;
        }

        public Boolean getHasWeaponDamage() {
            return hasWeaponDamage;
        }

        public void setHasWeaponDamage(Boolean hasWeaponDamage) {
            this.hasWeaponDamage = hasWeaponDamage;
        }

        public List<Object> getNotifications() {
            return notifications;
        }

        public void setNotifications(List<Object> notifications) {
            this.notifications = notifications;
        }
    }

    public static class KillStreak {

        @Field("steam_id")
        @JsonProperty("steamid")
        private String steamId;

        private Long streak;

        /**
         * Seconds since match start
         */
        private Long time;

        public String getSteamId() {
            return steamId;
        }

        public void setSteamId(String steamId) {
            this.steamId = steamId;
        }

        public Long getStreak() {
            return streak;
        }

        public void setStreak(Long streak) {
            this.streak = streak;
        }

        public Long getTime() {
            return time;
        }

        public void setTime(Long time) {
            this.time = time;
        }
    }
}
