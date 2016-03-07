package com.ugcleague.ops.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Document(collection = "settings")
public class Settings {

    @Id
    private String id;

    @Field("last_announcement")
    private Map<String, Announcement> lastAnnouncement = new LinkedHashMap<>();

    @Field("last_user_message")
    private Map<String, ZonedDateTime> lastUserMessage = new LinkedHashMap<>();

    private Map<String, Object> values = new LinkedHashMap<>();

    @Field("update_data_map")
    private Map<String, ServerUpdateData> updateDataMap = new LinkedHashMap<>();

    @Field("check_data_map")
    private Map<String, ServerCheckData> checkDataMap = new LinkedHashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Announcement> getLastAnnouncement() {
        return lastAnnouncement;
    }

    public void setLastAnnouncement(Map<String, Announcement> lastAnnouncement) {
        this.lastAnnouncement = lastAnnouncement;
    }

    public Map<String, ZonedDateTime> getLastUserMessage() {
        return lastUserMessage;
    }

    public void setLastUserMessage(Map<String, ZonedDateTime> lastUserMessage) {
        this.lastUserMessage = lastUserMessage;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    public Map<String, ServerUpdateData> getUpdateDataMap() {
        return updateDataMap;
    }

    public void setUpdateDataMap(Map<String, ServerUpdateData> updateDataMap) {
        this.updateDataMap = updateDataMap;
    }

    public Map<String, ServerCheckData> getCheckDataMap() {
        return checkDataMap;
    }

    public void setCheckDataMap(Map<String, ServerCheckData> checkDataMap) {
        this.checkDataMap = checkDataMap;
    }

    @Override
    public String toString() {
        return "Settings{" +
            "id='" + id + '\'' +
            ", lastAnnouncement=" + lastAnnouncement +
            ", lastUserMessage=" + lastUserMessage +
            ", values=" + values +
            ", updateDataMap=" + updateDataMap +
            ", checkDataMap=" + checkDataMap +
            '}';
    }

    public static class Announcement {

        private ZonedDateTime time = ZonedDateTime.now();
        private String message;

        public Announcement() {
            this("");
        }

        public Announcement(String message) {
            this.message = message;
        }

        public ZonedDateTime getTime() {
            return time;
        }

        public void setTime(ZonedDateTime time) {
            this.time = time;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "Announcement{" +
                "time=" + time +
                ", message='" + message + '\'' +
                '}';
        }
    }

    public static class ServerUpdateData {

        private int attempts = 0;
        private ZonedDateTime lastRconAnnounce = null;

        public int getAttempts() {
            return attempts;
        }

        public void setAttempts(int attempts) {
            this.attempts = attempts;
        }

        public ZonedDateTime getLastRconAnnounce() {
            return lastRconAnnounce;
        }

        public void setLastRconAnnounce(ZonedDateTime lastRconAnnounce) {
            this.lastRconAnnounce = lastRconAnnounce;
        }

        @Override
        public String toString() {
            return "ServerUpdateData{" +
                "attempts=" + attempts +
                ", lastRconAnnounce=" + lastRconAnnounce +
                '}';
        }
    }

    public static class ServerCheckData {

        private int attempts = 0;
        private ZonedDateTime firstAttempt = ZonedDateTime.now();

        public int getAttempts() {
            return attempts;
        }

        public void setAttempts(int attempts) {
            this.attempts = attempts;
        }

        public ZonedDateTime getFirstAttempt() {
            return firstAttempt;
        }

        public void setFirstAttempt(ZonedDateTime firstAttempt) {
            this.firstAttempt = firstAttempt;
        }

        @Override
        public String toString() {
            return "ServerCheckData{" +
                "attempts=" + attempts +
                ", firstAttempt=" + firstAttempt +
                '}';
        }
    }
}
