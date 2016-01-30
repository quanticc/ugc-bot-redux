package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public class UgcResult extends AbstractAuditingEntity implements Serializable {

    @Id
    private Integer id;

    @Field("match_id")
    private Integer matchId;

    @Field("schedule_id")
    private Integer scheduleId;

    @Field("schedule_date")
    private ZonedDateTime scheduleDate;

    @Field("map_name")
    private String mapName;

    @Field("home_clan_id")
    private Integer homeClanId;

    @Field("away_clan_id")
    private Integer awayClanId;

    @Field("home_team")
    private String homeTeam;

    @Field("away_team")
    private String awayTeam;

    @Size(min = 3, max = 3)
    @Field("home_scores")
    private List<Integer> homeScores;

    @Size(min = 3, max = 3)
    @Field("away_scores")
    private List<Integer> awayScores;

    @Field("winner")
    private String winner;

    @Field("winner_clan_id")
    private Integer winnerClanId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public Integer getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Integer scheduleId) {
        this.scheduleId = scheduleId;
    }

    public ZonedDateTime getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(ZonedDateTime scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public Integer getHomeClanId() {
        return homeClanId;
    }

    public void setHomeClanId(Integer homeClanId) {
        this.homeClanId = homeClanId;
    }

    public Integer getAwayClanId() {
        return awayClanId;
    }

    public void setAwayClanId(Integer awayClanId) {
        this.awayClanId = awayClanId;
    }

    public String getHomeTeam() {
        return homeTeam;
    }

    public void setHomeTeam(String homeTeam) {
        this.homeTeam = homeTeam;
    }

    public String getAwayTeam() {
        return awayTeam;
    }

    public void setAwayTeam(String awayTeam) {
        this.awayTeam = awayTeam;
    }

    public List<Integer> getHomeScores() {
        return homeScores;
    }

    public void setHomeScores(List<Integer> homeScores) {
        this.homeScores = homeScores;
    }

    public List<Integer> getAwayScores() {
        return awayScores;
    }

    public void setAwayScores(List<Integer> awayScores) {
        this.awayScores = awayScores;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public Integer getWinnerClanId() {
        return winnerClanId;
    }

    public void setWinnerClanId(Integer winnerClanId) {
        this.winnerClanId = winnerClanId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UgcResult ugcResult = (UgcResult) o;
        return Objects.equals(id, ugcResult.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UgcResult{" +
            "id=" + id +
            ", matchId=" + matchId +
            ", scheduleId=" + scheduleId +
            ", scheduleDate=" + scheduleDate +
            ", mapName='" + mapName + '\'' +
            ", homeClanId=" + homeClanId +
            ", awayClanId=" + awayClanId +
            ", homeTeam='" + homeTeam + '\'' +
            ", awayTeam='" + awayTeam + '\'' +
            ", homeScores=" + homeScores +
            ", awayScores=" + awayScores +
            ", winner='" + winner + '\'' +
            ", winnerClanId=" + winnerClanId +
            '}';
    }
}
