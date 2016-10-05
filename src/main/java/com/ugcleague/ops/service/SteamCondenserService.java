package com.ugcleague.ops.service;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.exceptions.WebApiException;
import com.github.koraktor.steamcondenser.steam.SteamPlayer;
import com.github.koraktor.steamcondenser.steam.community.WebApi;
import com.google.gson.Gson;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.service.util.SourceServer;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * A collection of methods available from the Steam Condenser library, wrapped in checks and enhanced with refreshing and retrying
 * capabilities.
 *
 * @author Ivan Abarca
 */
@Service
public class SteamCondenserService {
    private static final Logger log = LoggerFactory.getLogger(SteamCondenserService.class);

    private final Map<String, SourceServer> sourceServers = new ConcurrentHashMap<>();
    private final LeagueProperties leagueProperties;

    private Integer lastCachedVersion = 0;

    @Autowired
    public SteamCondenserService(LeagueProperties leagueProperties) throws WebApiException {
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void configure() {
        try {
            WebApi.setApiKey(leagueProperties.getGameServers().getSteamApiKey());
        } catch (WebApiException e) {
            log.error("Invalid Steam API key", e);
        }
    }

    /**
     * Ping the <code>server</code> using the underlying {@link SourceServer#getPing()} method.
     *
     * @param server a server instance
     * @return the latency of this server in milliseconds
     */
    public Integer ping(SourceServer server) {
        try {
            return throwingPing(server);
        } catch (Exception e) {
            return -2;
        }
    }

    @Retryable(backoff = @Backoff(2000L))
    private Integer throwingPing(SourceServer server) throws IOException, SteamCondenserException, TimeoutException {
        if (server != null) {
            server.updatePing();
            return server.getPing();
        } else {
            return -1;
        }
    }

    /**
     * Get the number of connected players using the underlying {@link SourceServer#getPlayers()} method.
     *
     * @param server a server instance
     * @return the amount of players on this server
     */
    public Integer players(SourceServer server) {
        try {
            return throwingPlayers(server);
        } catch (Exception e) {
            return -2;
        }
    }

    @Retryable(backoff = @Backoff(2000L))
    private Integer throwingPlayers(SourceServer server) throws IOException, SteamCondenserException, TimeoutException {
        if (server != null) {
            server.updatePlayers();
            return server.getPlayers().size();
        } else {
            return -1;
        }
    }

    /**
     * Returns a list of players currently playing on this server.
     *
     * @param server       a server instance
     * @param rconPassword the RCON password of the server, can be <code>null</code>
     * @return The players on this server, could be empty if the request failed after retrying
     */
    public Map<String, SteamPlayer> playerList(SourceServer server, String rconPassword) {
        try {
            return throwingPlayerList(server, rconPassword);
        } catch (Exception e) {
            log.warn("Could not get player list after retrying: {}", e.toString());
            return Collections.emptyMap();
        }
    }

    @Retryable(backoff = @Backoff(2000L))
    private Map<String, SteamPlayer> throwingPlayerList(SourceServer server, String rconPassword)
        throws IOException, SteamCondenserException, TimeoutException {
        // if the credentials are invalid this will fall back to simple player query (no SteamIDs)
        server.updatePlayers(rconPassword);
        return server.getPlayers(rconPassword);
    }

    public Map<String, String> rules(SourceServer server) {
        try {
            return throwingRules(server);
        } catch (Exception e) {
            log.warn("Could not get server rules after retrying: {}", e.toString());
            return Collections.emptyMap();
        }
    }

    @Retryable(backoff = @Backoff(2000L))
    private Map<String, String> throwingRules(SourceServer server) throws IOException, SteamCondenserException, TimeoutException {
        server.updateRules();
        return server.getRules();
    }

    public Map<String, Object> info(SourceServer server) {
        try {
            return throwingInfo(server);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @Retryable(backoff = @Backoff(2000L))
    private Map<String, Object> throwingInfo(SourceServer server) throws IOException, SteamCondenserException, TimeoutException {
        server.updateServerInfo();
        return server.getServerInfo();
    }

    /**
     * Executes a RCON command to the server.
     *
     * @param server       a server instance
     * @param rconPassword the RCON password of the server
     * @param command      the output of the executed command
     * @return the output of the executed command
     * @throws SteamCondenserException if the request fails
     * @throws TimeoutException        if the request times out
     * @throws RemoteAccessException   if <strong>rconPassword</strong> is invalid
     */
    @Retryable(backoff = @Backoff(2000L), exclude = {RemoteAccessException.class})
    public String rcon(SourceServer server, String rconPassword, String command)
        throws SteamCondenserException, TimeoutException, RemoteAccessException {
        if (server.rconAuth(rconPassword)) {
            return server.rconExec(command);
        }
        // rcon_password is wrong! don't retry
        throw new RemoteAccessException("Invalid RCON password");
    }

    public SourceServer getSourceServer(String address) {
        return sourceServers.computeIfAbsent(address, this::createSourceServer);
    }

    public boolean containsSourceServer(String address) {
        return sourceServers.containsKey(address);
    }

    private SourceServer createSourceServer(String address) {
        log.debug("Creating server socket at {}", address);
        try {
            return retryCreateSourceServer(address);
        } catch (SteamCondenserException e) {
            log.warn("Could not create socket after retrying", e);
        }
        return null;
    }

    @Retryable(backoff = @Backoff(2000L))
    private SourceServer retryCreateSourceServer(String address) throws SteamCondenserException {
        return new SourceServer(address);
    }

    private Integer loadLatestVersion() throws JSONException, SteamCondenserException {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", 440);
        params.put("version", 1);
        String json = WebApi.getJSON("ISteamApps", "UpToDateCheck", 1, params);
        JSONObject result = new JSONObject(json).getJSONObject("response");
        if (!result.getBoolean("success")) {
            throw new SteamCondenserException(result.getString("error"));
        }
        return result.getInt("required_version");
    }

    public Integer getLatestVersion() {
        try {
            lastCachedVersion = loadLatestVersion();
        } catch (JSONException | SteamCondenserException e) {
            log.warn("Could not get latest version number: {}", e.toString());
        }
        return lastCachedVersion;
    }

    public boolean isLatestVersion(Integer version) {
        Integer latest = getLatestVersion();
        return latest != null && version >= latest;
    }

    public Integer getLastCachedVersion() {
        return lastCachedVersion;
    }

    public List<SteamFriend> getFriends(String steamId64) throws WebApiException {
        Map<String, Object> params = new HashMap<>();
        params.put("steamid", steamId64);
        String json = WebApi.getJSON("ISteamUser", "GetFriendList", 1, params);
        FriendsListResponse response = new Gson().fromJson(json, FriendsListResponse.class);
        return response.friendslist.friends.get("friends");
    }

    public static class FriendsListResponse {
        public FriendsList friendslist;
    }

    public static class FriendsList {
        public Map<String, List<SteamFriend>> friends;
    }

    public static class SteamFriend {
        public String steamid;
        public String relationship;
        public long friend_since;
    }
}
