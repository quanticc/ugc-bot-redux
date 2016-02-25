package com.ugcleague.ops.service;

import com.ugcleague.ops.config.Constants;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.document.LogsTfStats;
import com.ugcleague.ops.service.util.LogsTfMatchIterator;
import com.ugcleague.ops.web.rest.JsonLogsTfMatchesResponse;
import com.ugcleague.ops.web.rest.LogsTfMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Service
public class LogsTfApiClient {

    private static final Logger log = LoggerFactory.getLogger(LogsTfApiClient.class);

    private final LeagueProperties properties;

    private String matchesUrl;
    private String statsUrl;

    @Autowired
    public LogsTfApiClient(LeagueProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    private void configure() {
        Map<String, String> endpoints = properties.getStats().getEndpoints();
        matchesUrl = endpoints.get("logsTfMatches");
        statsUrl = endpoints.get("logsTfStats");
    }

    public Iterable<LogsTfMatch> getMatchIterable(long steamId64, int chunkSize) {
        return () -> new LogsTfMatchIterator(this, steamId64, chunkSize);
    }

    @Retryable(maxAttempts = 10, backoff = @Backoff(2000L))
    public List<LogsTfMatch> getMatches(long steamId64, int limit) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", Constants.USER_AGENT);
        ResponseEntity<JsonLogsTfMatchesResponse> response = restTemplate.exchange(matchesUrl, HttpMethod.GET,
            new HttpEntity<>(headers), JsonLogsTfMatchesResponse.class, steamId64, limit);
        JsonLogsTfMatchesResponse body = response.getBody();
        if (!body.isSuccess()) {
            log.warn("Response indicates fail status: ({}, {})", steamId64, limit);
        }
        return body.getLogs();
    }

    @Retryable(maxAttempts = 10, backoff = @Backoff(2000L))
    public LogsTfStats getStats(long id) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", Constants.USER_AGENT);
        ResponseEntity<LogsTfStats> response = restTemplate.exchange(statsUrl, HttpMethod.GET,
            new HttpEntity<>(headers), LogsTfStats.class, id);
        LogsTfStats stats = response.getBody();
        stats.setId(id);
        return stats;
    }
}
