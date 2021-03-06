package com.ugcleague.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcleague.ops.config.Constants;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.document.UgcPlayer;
import com.ugcleague.ops.domain.document.UgcResult;
import com.ugcleague.ops.domain.document.UgcTeam;
import com.ugcleague.ops.web.rest.JsonUgcResponse;
import com.ugcleague.ops.web.rest.UgcPlayerPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class UgcApiClient {

    private static final Logger log = LoggerFactory.getLogger(UgcApiClient.class);

    private final LeagueProperties properties;
    private final ObjectMapper mapper;

    private String teamRosterUrl;
    private String teamPageUrl;
    private String matchResultsUrl;
    private String existsUrl;
    private String playerTeamHistoryUrl;
    private String playerTeamCurrentUrl;
    private String playerTeamCurrentActiveUrl;
    private String teamPlayerUrl;

    @Autowired
    public UgcApiClient(LeagueProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @PostConstruct
    private void configure() {
        Map<String, String> endpoints = properties.getUgc().getEndpoints();
        // load endpoints
        // v1 API
        teamRosterUrl = endpoints.get("teamRoster"); // clan_id
        teamPageUrl = endpoints.get("teamPage"); // clan_id
        matchResultsUrl = endpoints.get("matchResults"); // week, season
        teamPlayerUrl = endpoints.get("teamPlayer"); // id64
        // v2 API
        existsUrl = endpoints.get("exists"); // key, id64
        playerTeamHistoryUrl = endpoints.get("playerTeamHistory"); // key, id64
        playerTeamCurrentUrl = endpoints.get("playerTeamCurrent"); // key, id64
        playerTeamCurrentActiveUrl = endpoints.get("playerTeamCurrentActive"); // id64
    }

    @Retryable(maxAttempts = 10, backoff = @Backoff(2000L))
    private String httpToString(String url) throws IOException {
        try {
            // TODO: make it configurable
            Thread.sleep(250);
        } catch (InterruptedException e) {
            log.warn("Interrupted my sleep", e);
        }
        URL u = new URL(url);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("Content-length", "0");
        c.setUseCaches(false);
        c.setAllowUserInteraction(false);
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent",
            Constants.USER_AGENT);
        c.connect();
        int status = c.getResponseCode();
        switch (status) {
            case 200:
            case 201:
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    return sb.toString();
                }
            case 403:
            case 404:
                throw new IOException("Returned status: " + status);
        }
        return "";
    }

    public Set<UgcResult> getMatchResults(int season, int week) {
        return rawMatchResults(season, week).map(this::mapMatchResults).orElseGet(LinkedHashSet::new);
    }

    private Set<UgcResult> mapMatchResults(JsonUgcResponse response) {
        Set<UgcResult> results = new LinkedHashSet<>();
        for (List<Object> raw : response.getData()) {
            UgcResult result = new UgcResult();
            result.setMatchId(safeToInteger(raw.get(0))); // "MATCH_ID", #
            result.setScheduleId(safeToInteger(raw.get(1))); // "SCHED_ID", #
            result.setScheduleDate(parseLongDate(safeToString(raw.get(2)))); // "SCHED_DT", date
            result.setMapName(safeToString(raw.get(3))); // "MAP_NAME", str
            result.setHomeClanId(safeToInteger(raw.get(4))); // "CLAN_ID_H", #
            result.setHomeTeam(safeToString(raw.get(5))); // "HOME_TEAM", str
            result.setAwayClanId(safeToInteger(raw.get(6))); // "CLAN_ID_V", #
            int h1 = safeToInteger(raw.get(7)); // "NO_SCORE_R1_H", #
            int h2 = safeToInteger(raw.get(8)); // "NO_SCORE_R2_H", #
            int h3 = safeToInteger(raw.get(9)); // "NO_SCORE_R3_H", #
            result.setHomeScores(Arrays.asList(h1, h2, h3));
            result.setAwayTeam(safeToString(raw.get(10))); // "VISITING_TEAM", str
            int a1 = safeToInteger(raw.get(11)); // "NO_SCORE_R1_V", #
            int a2 = safeToInteger(raw.get(12)); // "NO_SCORE_R2_V", #
            int a3 = safeToInteger(raw.get(13)); // "NO_SCORE_R3_V", #
            result.setAwayScores(Arrays.asList(a1, a2, a3));
            result.setWinnerClanId(safeToInteger(raw.get(14))); // "WINNER", #
            result.setWinner(safeToString(raw.get(15))); // "WINNING_TEAM" str
            result.setId(result.getMatchId());
            results.add(result);
        }
        return results;
    }

    private Optional<JsonUgcResponse> rawMatchResults(int season, int week) {
        String url = matchResultsUrl.replace("{week}", week + "").replace("{season}", season + "");
        try {
            return Optional.of(mapper.readValue(httpToString(url), JsonUgcResponse.class));
        } catch (IOException e) {
            log.warn("Could not get s{}w{} results: {}", season, week, e.toString());
        }
        return Optional.empty();
    }

    private ZonedDateTime parseLongDate(String text) {
        try {
            LocalDateTime actual = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("MMMM, dd yyyy HH:mm:ss", Locale.ENGLISH));
            return actual.atZone(ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            log.warn("Could not extract date from {}: {}", text, e.toString());
        }
        return ZonedDateTime.now();
    }

//    @Retryable(maxAttempts = 10, backoff = @Backoff(2000L))
//    public UgcPlayerPage getCurrentPlayer(Long steamId64) {
//        String key = properties.getUgc().getKey();
//        RestTemplate restTemplate = new RestTemplate();
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("User-Agent", Constants.USER_AGENT);
//        ResponseEntity<UgcPlayerPage> response = restTemplate.exchange(playerTeamCurrentUrl, HttpMethod.GET,
//            new HttpEntity<>(headers), UgcPlayerPage.class, key, steamId64);
//        return response.getBody();
//    }

    public UgcPlayerPage getCurrentPlayer(Long steamId64) {
        return rawCurrentPlayer(steamId64).map(this::mapCurrentPlayer).orElse(null);
    }

    private UgcPlayerPage mapCurrentPlayer(JsonUgcResponse response) {
        List<List<Object>> data = response.getData();
        if (data.size() < 1) {
            log.warn("Not enough data to create player roster info: {}", response);
            return null;
        }
        UgcPlayerPage player = new UgcPlayerPage();
        player.setUgcPage("http://www.ugcleague.com/players_page.cfm?player_id=" + safeToLong(data.get(0).get(4)));
        player.setTeam(new ArrayList<>());
        for (List<Object> raw : data) {
            if ("N".equals(safeToString(raw.get(5)))) {
                UgcPlayerPage.Team team = new UgcPlayerPage.Team();
                team.setClanId(safeToInteger(raw.get(1)));
                team.setMemberName(safeToString(raw.get(2)));
                team.setJoined(safeToString(raw.get(6))); // alternative format: "February, 19 2016 19:28:58"
                team.setTag(safeToString(raw.get(7)));
                team.setName(safeToString(raw.get(8)));
                team.setFormat(safeToString(raw.get(9)));
                team.setDivision(safeToString(raw.get(10)));
                player.getTeam().add(team);
            }
        }
        return player;
    }

    private Optional<JsonUgcResponse> rawCurrentPlayer(Long steamId64) {
        String url = teamPlayerUrl.replace("{id64}", steamId64 + "");
        try {
            return Optional.of(mapper.readValue(httpToString(url), JsonUgcResponse.class));
        } catch (IOException e) {
            log.warn("Could not get roster info for player id {}. Raw results: {}", steamId64, e.toString());
        }
        return Optional.empty();
    }

    public Optional<UgcTeam> getTeamPage(int id) {
        return rawTeamPage(id).map(this::mapTeamPage);
    }

    private UgcTeam mapTeamPage(JsonUgcResponse response) {
        List<List<Object>> data = response.getData();
        if (data.size() < 1) {
            log.warn("Not enough data to create team page: {}", response);
            return null;
        }
        List<Object> raw = data.get(0);
        UgcTeam team = new UgcTeam();
        team.setId(safeToInteger(raw.get(0)));
        team.setTag(safeToString(raw.get(1)));
        team.setName(safeToString(raw.get(2)));
        team.setStatus(safeToString(raw.get(3)));
        team.setSteamPage(safeToString(raw.get(6)).replace("\\", ""));
        team.setAvatar(safeToString(raw.get(9)).replace("\\", ""));
        team.setTimezone(safeToString(raw.get(12)));
        team.setLadderName(safeToString(raw.get(15)));
        team.setDivisionName(safeToString(raw.get(16)));
        return team;
    }

    private Optional<JsonUgcResponse> rawTeamPage(int id) {
        String url = teamPageUrl.replace("{id}", id + "");
        try {
            return Optional.of(mapper.readValue(clean(httpToString(url)), JsonUgcResponse.class));
        } catch (IOException e) {
            log.warn("Could not get team id {}. Raw results: {}", id, e.toString());
        }
        return Optional.empty();
    }

    private String clean(String input) {
        return input.replaceAll("(^onLoad\\()|(\\)$)", "");
    }

    public List<UgcPlayer> getTeamRoster(int id) {
        return rawTeamRoster(id).map(this::mapTeamRoster).orElseGet(ArrayList::new);
    }

    private List<UgcPlayer> mapTeamRoster(JsonUgcResponse response) {
        List<UgcPlayer> list = new ArrayList<>();
        for (List<Object> raw : response.getData()) {
            UgcPlayer player = new UgcPlayer();
            player.setName(safeToString(raw.get(0)));
            player.setType(safeToString(raw.get(1)));
            player.setAdded(parseLongDate(safeToString(raw.get(2))));
            player.setUpdated(parseLongDate(safeToString(raw.get(3))));
            player.setId(safeToLong(raw.get(5)));
            list.add(player);
        }
        return list;
    }

    private Optional<JsonUgcResponse> rawTeamRoster(int id) {
        String url = teamRosterUrl.replace("{id}", id + "");
        try {
            return Optional.of(mapper.readValue(httpToString(url), JsonUgcResponse.class));
        } catch (IOException e) {
            log.warn("Could not get roster for team id {}. Raw results: {}", id, e.toString());
        }
        return Optional.empty();
    }

    private String safeToString(Object obj) {
        return obj == null ? "?" : obj.toString();
    }

    private Integer safeToInteger(Object obj) {
        if (obj == null) {
            return 0;
        }
        try {
            if (obj instanceof String) {
                return Integer.parseInt((String) obj);
            } else {
                return (Integer) obj;
            }
        } catch (NumberFormatException | ClassCastException e) {
            log.warn("Could not parse or cast {} to integer: {}", obj, e.toString());
            return 0;
        }
    }

    private Long safeToLong(Object obj) {
        if (obj == null) {
            return 0L;
        }
        try {
            if (obj instanceof String) {
                return Long.parseLong((String) obj);
            } else {
                return (Long) obj;
            }
        } catch (NumberFormatException | ClassCastException e) {
            log.warn("Could not parse or cast {} to long: {}", obj, e.toString());
            return 0L;
        }
    }
}
