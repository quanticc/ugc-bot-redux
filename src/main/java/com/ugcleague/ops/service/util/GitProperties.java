package com.ugcleague.ops.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class GitProperties {

    private static final Logger log = LoggerFactory.getLogger(GitProperties.class);

    private final Properties properties;

    public GitProperties() {
        this.properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/git.properties")) {
            if (input != null) {
                properties.load(input);
                log.debug("Loaded Git properties: {}", properties.toString());
            } else {
                log.warn("Could not load version properties");
            }
        } catch (IOException e) {
            log.warn("Git properties could not be loaded: {}", e.toString());
        }
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
