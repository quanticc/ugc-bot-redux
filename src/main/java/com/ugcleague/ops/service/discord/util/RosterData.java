package com.ugcleague.ops.service.discord.util;

import com.ugcleague.ops.web.rest.UgcPlayerPage;

public class RosterData {

    private String serverName = "";
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
}
