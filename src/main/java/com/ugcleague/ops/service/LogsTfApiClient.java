package com.ugcleague.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcleague.ops.config.LeagueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

@Service
public class LogsTfApiClient {

    private static final Logger log = LoggerFactory.getLogger(LogsTfApiClient.class);

    private final LeagueProperties properties;
    private final ObjectMapper mapper;

    private String matchesUrl;
    private String statsUrl;

    @Autowired
    public LogsTfApiClient(LeagueProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @PostConstruct
    private void configure() {
        Map<String, String> endpoints = properties.getStats().getEndpoints();
        matchesUrl = endpoints.get("logsTfMatches");
        statsUrl = endpoints.get("logsTfStats");
    }
}
