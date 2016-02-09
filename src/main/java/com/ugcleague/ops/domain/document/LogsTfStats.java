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

    @JsonProperty("teams")
    private Map<String, Team> teams = new LinkedHashMap<>();

    @JsonProperty("length")
    private Long length;

    @JsonProperty("players")
    private Map<String, Player> players = new LinkedHashMap<>();

    @JsonProperty("names")
    private Map<String, String> names = new LinkedHashMap<>();

    @JsonProperty("rounds")
    private List<Round> rounds = new ArrayList<>();

    // steam_id -> steam_id -> heals
    @JsonProperty("healspread")
    private Map<String, Map<String, Long>> healspread = new LinkedHashMap<>();

    // steam_id -> class -> kills
    @Field("class_kills")
    @JsonProperty("classkills")
    private Map<String, Map<String, Long>> classKills = new LinkedHashMap<>();

    // steam_id -> class -> deaths
    @Field("class_deaths")
    @JsonProperty("classdeaths")
    private Map<String, Map<String, Long>> classDeaths = new LinkedHashMap<>();

    @JsonProperty("chat")
    private List<Chat> chat = new ArrayList<>();

    @JsonProperty("info")
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

    public static class Team {

        @JsonProperty("score")
        private Long score;

        @JsonProperty("kills")
        private Long kills;

        @JsonProperty("deaths")
        private Long deaths;

        @JsonProperty("dmg")
        private Long dmg;

        @JsonProperty("charges")
        private Long charges;

        @Field("first_caps")
        @JsonProperty("firstcaps")
        private Long firstCaps;

        @JsonProperty("caps")
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

    public static class Player {

        @JsonProperty("team")
        private String team;

        @JsonProperty("kills")
        private Long kills;

        @JsonProperty("deaths")
        private Long deaths;

        @JsonProperty("assists")
        private Long assists;

        @JsonProperty("kapd")
        private String kapd;

        @JsonProperty("kpd")
        private String kpd;

        @JsonProperty("dmg")
        private Long dmg;

        @JsonProperty("lks")
        private Long lks;

        @JsonProperty("dapd")
        private Long dapd;

        @JsonProperty("dapm")
        private Long dapm;

        @JsonProperty("ubers")
        private Long ubers;

        @JsonProperty("drops")
        private Long drops;

        @JsonProperty("backstabs")
        private Long backstabs;

        @JsonProperty("headshots")
        private Long headshots;

        @JsonProperty("sentries")
        private Long sentries;

        @JsonProperty("heal")
        private Long heal;

        @JsonProperty("cpc")
        private Long cpc;

        @JsonProperty("ic")
        private Long ic;

        @JsonProperty("medkits")
        private Long medkits;

        @Field("class_stats")
        @JsonProperty("class_stats")
        private List<ClassStat> classStats = new ArrayList<>();

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

        public List<ClassStat> getClassStats() {
            return classStats;
        }

        public void setClassStats(List<ClassStat> classStats) {
            this.classStats = classStats;
        }
    }

    public static class ClassStat {

        @JsonProperty("kills")
        private Long kills;

        @JsonProperty("assists")
        private Long assists;

        @JsonProperty("deaths")
        private Long deaths;

        @JsonProperty("dmg")
        private Long dmg;

        @JsonProperty("weapon")
        private Map<String, Long> weapon = new LinkedHashMap<>();

        @Field("total_time")
        @JsonProperty("total_time")
        private Long totalTime;

        @JsonProperty("type")
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

        public Map<String, Long> getWeapon() {
            return weapon;
        }

        public void setWeapon(Map<String, Long> weapon) {
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

        @Field("start_time")
        @JsonProperty("start_time")
        private Long startTime;

        @JsonProperty("winner")
        private String winner;

        @JsonProperty("team")
        private Team team = new Team();

        @JsonProperty("events")
        private List<Map<String, Object>> events = new ArrayList<>();

        @JsonProperty("players")
        private Map<String, PlayerRound> players = new LinkedHashMap<>();

        @JsonProperty("firstcap")
        private String firstcap;

        @JsonProperty("length")
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

        @JsonProperty("kills")
        private Long kills;

        @JsonProperty("dmg")
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

        @JsonProperty("name")
        private String name;

        @JsonProperty("msg")
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

    public static class Info {

        @JsonProperty("map")
        private String map;

        @JsonProperty("supplemental")
        private Boolean supplemental;

        @Field("total_length")
        @JsonProperty("total_length")
        private Long totalLength;

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
    }

    public static class KillStreak {

        @Field("steam_id")
        @JsonProperty("steamid")
        private String steamId;

        @JsonProperty("streak")
        private Long streak;

        @JsonProperty("time")
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
