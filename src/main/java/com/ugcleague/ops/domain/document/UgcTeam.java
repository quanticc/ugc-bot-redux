package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Partial representation of UGC team data
 */
@Document(collection = "ugc_team")
public class UgcTeam extends AbstractAuditingEntity {

    @Id
    private Integer id;
    private String tag;
    @Indexed
    private String name;
    private String status;
    @Field("steam_page")
    private String steamPage;
    private String avatar;
    private String timezone = "";
    @Indexed
    @Field("ladder_name")
    private String ladderName;
    @Indexed
    @Field("division_name")
    private String divisionName;
    @DBRef
    private List<UgcPlayer> roster = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSteamPage() {
        return steamPage;
    }

    public void setSteamPage(String steamPage) {
        this.steamPage = steamPage;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLadderName() {
        return ladderName;
    }

    public void setLadderName(String ladderName) {
        this.ladderName = ladderName;
    }

    public String getDivisionName() {
        return divisionName;
    }

    public void setDivisionName(String divisionName) {
        this.divisionName = divisionName;
    }

    public List<UgcPlayer> getRoster() {
        return roster;
    }

    public void setRoster(List<UgcPlayer> roster) {
        this.roster = roster;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UgcTeam ugcTeam = (UgcTeam) o;
        return Objects.equals(id, ugcTeam.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UgcTeam{" +
            "id=" + id +
            ", tag='" + tag + '\'' +
            ", name='" + name + '\'' +
            ", status='" + status + '\'' +
            ", steamPage='" + steamPage + '\'' +
            ", avatar='" + avatar + '\'' +
            ", timezone='" + timezone + '\'' +
            ", ladderName='" + ladderName + '\'' +
            ", divisionName='" + divisionName + '\'' +
            ", cacheCreated=" + getCreatedDate() +
            ", cacheLastModified=" + getLastModifiedDate() +
            '}';
    }
}
