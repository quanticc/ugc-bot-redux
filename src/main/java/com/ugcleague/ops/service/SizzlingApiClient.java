package com.ugcleague.ops.service;

import com.ugcleague.ops.config.Constants;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.document.SizzStats;
import com.ugcleague.ops.service.util.SizzMatchIterator;
import com.ugcleague.ops.service.util.SteamIdConverter;
import com.ugcleague.ops.web.rest.JsonSizzMatchesResponse;
import com.ugcleague.ops.web.rest.JsonSizzStatsResponse;
import com.ugcleague.ops.web.rest.SizzMatch;
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

/**
 * Provides interaction with the SizzlingStats API.
 */
@Service
public class SizzlingApiClient {

    private static final Logger log = LoggerFactory.getLogger(SizzlingApiClient.class);

    private final LeagueProperties properties;

    private String latestMatchesUrl;
    private String playerLastMatchesUrl;
    private String playerMatchesUrl;
    private String statsUrl;

    @Autowired
    public SizzlingApiClient(LeagueProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    private void configure() {
        Map<String, String> endpoints = properties.getStats().getEndpoints();
        latestMatchesUrl = endpoints.get("ssLatestMatches");
        playerLastMatchesUrl = endpoints.get("ssPlayerLastMatches");
        playerMatchesUrl = endpoints.get("ssPlayerMatches");
        statsUrl = endpoints.get("ssStats");
    }

    /**
     * Obtains an iterable to successively retrieve match data for a player as it is needed.
     *
     * @param steamId64 a 64-bit Steam Community ID of the player
     * @return an iterable of matches, that can be used to retrieve the full stats
     */
    public Iterable<SizzMatch> getMatchIterable(long steamId64) {
        return () -> new SizzMatchIterator(this, steamId64);
    }

    /**
     * Get a list of SizzlingStats match data for a player. Allows skipping a number of entries for pagination.
     *
     * @param steamId64 a 64-bit Steam Community ID of the player
     * @param skip      the number of matches to skip before getting the list of matches
     * @return a list of raw match data as they are obtained from the SizzlingStats API
     */
    @Retryable(maxAttempts = 10, backoff = @Backoff(2000L))
    public List<SizzMatch> getMatches(long steamId64, int skip) {
        String steam3 = SteamIdConverter.steamId64To3(steamId64);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", Constants.USER_AGENT);
        ResponseEntity<JsonSizzMatchesResponse> response = restTemplate.exchange(playerMatchesUrl, HttpMethod.GET,
            new HttpEntity<>(headers), JsonSizzMatchesResponse.class, steam3, Integer.MAX_VALUE, skip);
        return response.getBody().getMatches();
    }

    /**
     * Get stats for a SizzlingStats match by its ID.
     *
     * @param id the match ID
     * @return the match stats
     */
    @Retryable(maxAttempts = 10, backoff = @Backoff(2000L))
    public SizzStats getStats(long id) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", Constants.USER_AGENT);
        ResponseEntity<JsonSizzStatsResponse> response = restTemplate.exchange(statsUrl, HttpMethod.GET,
            new HttpEntity<>(headers), JsonSizzStatsResponse.class, id);
        return response.getBody().getStats();
    }
}