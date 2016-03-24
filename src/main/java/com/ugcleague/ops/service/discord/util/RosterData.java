package com.ugcleague.ops.service.discord.util;

import com.ugcleague.ops.web.rest.UgcPlayerPage;

import java.util.Objects;

public class RosterData {

    private String serverName = "<unnamed>";
    private String modernId = "";
    private Long communityId;
    private UgcPlayerPage ugcData = new UgcPlayerPage();

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getModernId() {
        return modernId;
    }

    public void setModernId(String modernId) {
        this.modernId = modernId;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public void setCommunityId(Long communityId) {
        this.communityId = communityId;
    }

    public UgcPlayerPage getUgcData() {
        return ugcData;
    }

    public void setUgcData(UgcPlayerPage ugcData) {
        this.ugcData = ugcData;
    }

    public RosterData updateUgcData(UgcPlayerPage ugcData) {
        if (ugcData != null) {
            setUgcData(ugcData);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RosterData that = (RosterData) o;
        return Objects.equals(modernId, that.modernId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modernId);
    }

    @Override
    public String toString() {
        return serverName + " " + modernId + " (" + communityId + ")";
    }
}
