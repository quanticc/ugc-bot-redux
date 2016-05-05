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
import java.util.Set;
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
            mapper.writeValue(Paths.get("config.json").toFile(), settings);
        } catch (IOException e) {
            log.warn("Could not write settings to file", e);
        }
    }

    public static class Settings {
        private final Set<String> soundBitesWhitelist = new ConcurrentSkipListSet<>();

        public Set<String> getSoundBitesWhitelist() {
            return soundBitesWhitelist;
        }
    }
}
