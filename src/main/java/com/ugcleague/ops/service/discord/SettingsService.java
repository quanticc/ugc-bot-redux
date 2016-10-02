package com.ugcleague.ops.service.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final ObjectMapper mapper;
    private volatile Settings settings = new Settings();

    @Autowired
    public SettingsService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    private void configure() {
        Path path = Paths.get("config.json");
        if (Files.exists(path)) {
            try {
                settings = mapper.readValue(path.toFile(), Settings.class);
            } catch (IOException e) {
                log.warn("Could not read settings from file", e);
            }
        }
    }

    public Settings getSettings() {
        return settings;
    }

    @PreDestroy
    private void cleanup() {
        try {
            mapper.writeValue(Paths.get("config." + System.currentTimeMillis() + ".json").toFile(), settings);
            mapper.writeValue(Paths.get("config.json").toFile(), settings);
        } catch (IOException e) {
            log.warn("Could not write settings to file", e);
        }
    }

    public static class Settings {
        private final Set<String> soundBitesWhitelist = new ConcurrentSkipListSet<>();
        private final Set<String> soundBitesBlacklist = new ConcurrentSkipListSet<>();
        private volatile String randomSoundDir = "audio";
        private final Map<String, Integer> playCount = new ConcurrentHashMap<>();
        private final Map<String, AnnounceData> lastAnnounce = new ConcurrentHashMap<>();
        private final Map<String, List<RollData>> rolls = new ConcurrentHashMap<>();
        private final Map<String, ResponseConfig> userToVoiceResponse = new ConcurrentHashMap<>();

        public Set<String> getSoundBitesWhitelist() {
            return soundBitesWhitelist;
        }

        public String getRandomSoundDir() {
            return randomSoundDir;
        }

        public void setRandomSoundDir(String randomSoundDir) {
            this.randomSoundDir = randomSoundDir;
        }

        public Map<String, Integer> getPlayCount() {
            return playCount;
        }

        public Map<String, AnnounceData> getLastAnnounce() {
            return lastAnnounce;
        }

        public Set<String> getSoundBitesBlacklist() {
            return soundBitesBlacklist;
        }

        public Map<String, List<RollData>> getRolls() {
            return rolls;
        }

        public Map<String, ResponseConfig> getUserToVoiceResponse() {
            return userToVoiceResponse;
        }
    }

    public static class ResponseConfig {
        private int chance = 10;
        private final List<String> responses = new ArrayList<>();
        private String channelId;

        public int getChance() {
            return chance;
        }

        public List<String> getResponses() {
            return responses;
        }

        public void setChance(int chance) {
            this.chance = chance;
        }

        public String getChannelId() {
            return channelId;
        }

        public void setChannelId(String channelId) {
            this.channelId = channelId;
        }
    }

    public static class AnnounceData {
        private final long timestamp;
        private final String message;

        public AnnounceData() {
            this("");
        }

        public AnnounceData(String message) {
            this.timestamp = System.currentTimeMillis();
            this.message = message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class RollData {
        private final long timestamp;
        private final String rollType;
        private final int result;

        public RollData() {
            this("", 0);
        }

        public RollData(String rollType, int result) {
            this.timestamp = System.currentTimeMillis();
            this.rollType = rollType;
            this.result = result;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getRollType() {
            return rollType;
        }

        public int getResult() {
            return result;
        }
    }
}
