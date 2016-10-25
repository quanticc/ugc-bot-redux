package com.ugcleague.ops.service;

import com.google.common.util.concurrent.RateLimiter;
import com.ugcleague.ops.config.Constants;
import com.ugcleague.ops.config.LeagueProperties;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parser for common server operations inside a GameServers web panel.
 *
 * @author Ivan Abarca
 */
@Service
public class AdminPanelService {

    private static final Logger log = LoggerFactory.getLogger(AdminPanelService.class);
    private static final String HOME_URL = "https://my.gameservers.com/home";
    private static final String SUB_URL = "https://my.gameservers.com/home/subscription_info.php";
    private static final int TIMEOUT = 10000;

    private final Map<String, String> session = new ConcurrentHashMap<>();
    private final RateLimiter rateLimiter = RateLimiter.create(0.5);
    private final LeagueProperties leagueProperties;
    private final RestOperations restTemplate;

    @Autowired
    public AdminPanelService(LeagueProperties leagueProperties, RestOperations restTemplate) {
        this.leagueProperties = leagueProperties;
        this.restTemplate = restTemplate;
    }

    /**
     * Retrieves the available server list from the provider. It should contain the most recent data.
     *
     * @return a map containing data obtained from the provider
     * @throws IOException
     */
    @Retryable(backoff = @Backoff(2000L))
    public Map<String, Map<String, String>> getServerDetails() throws IOException {
        rateLimiter.acquire();
        Document document = validateSessionAndGet(Jsoup.connect(HOME_URL).userAgent(Constants.USER_AGENT));
        Map<String, Map<String, String>> latest = new LinkedHashMap<>();
        // parse GameServers panel and cache available game servers
        for (Element server : document.select("td.section_notabs").select("tr")) {
            String text = "";
            for (Element data : server.select("td.datatbl_row")) {
                if (!data.text().isEmpty()) {
                    text = data.text();
                }
                Elements links = data.select("a");
                // only save servers with 3 links (info, config, mods)
                if (links.size() == 3) {
                    if (text.isEmpty()) {
                        log.warn("Server name not found!");
                        continue;
                    }
                    String subid = links.first().attr("href").split("SUBID=")[1];
                    // parse name & address
                    String[] array = text.split("\\[|\\] \\(|\\)");
                    // array[1] is the address, array[2] is the name of the server
                    Map<String, String> value = new HashMap<>();
                    value.put("name", array[2]);
                    value.put("SUBID", subid);
                    latest.put(array[1], value);
                    log.info("Server found: ({}) {} [{}]", subid, array[2], array[1]);
                }
            }
        }
        return latest;
    }

    /**
     * Retrieves information about the <code>server</code> not essential for Source/RCON socket creation but for associated
     * services like an FTP server operating for the server's location.
     *
     * @param subId the internal id GameServers uses for its servers
     * @return a map with configuration key-values found in the GS web page
     * @throws IOException
     */
    @Retryable(backoff = @Backoff(2000L))
    public Map<String, String> getServerInfo(String subId) throws IOException {
        rateLimiter.acquire();
        Map<String, String> map = new HashMap<>();
        Document document = validateSessionAndGet(
            Jsoup.connect(SUB_URL)
                .userAgent(Constants.USER_AGENT)
                .data("view", "server_information")
                .data("SUBID", subId)
                .timeout(TIMEOUT));
        Result result = extractResult(document.select("td.content_main").text());
        if (result != Result.SUCCESSFUL) {
            map.put("result", result.name());
        }
        Elements infos = document.select("div.server_info > a");
        String surl = infos.first().text();
        if (surl.startsWith("ftp://")) {
            URL url = new URL(surl);
            map.put("ftp-hostname", url.getHost());
            String[] userInfo = Optional.ofNullable(url.getUserInfo()).orElse("").split(":");
            if (userInfo.length == 2) {
                map.put("ftp-username", userInfo[0]);
                map.put("ftp-password", userInfo[1]);
            }
        }
        return map;
    }

    @Retryable(backoff = @Backoff(2000L))
    public String getServerConsole(String subId) throws IOException {
        rateLimiter.acquire();
        String bodyHtml = validateSessionAndGet(Jsoup.connect(SUB_URL)
            .userAgent(Constants.USER_AGENT)
            .data("view", "console_log")
            .data("SUBID", subId)
            .timeout(TIMEOUT)).body().toString();
        return Jsoup.clean(bodyHtml, "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
    }

    @Retryable(backoff = @Backoff(2000L))
    public synchronized Map<String, String> getServerConfig(String subId) throws IOException {
        rateLimiter.acquire();
        Map<String, String> map = new HashMap<>();
        Document document = validateSessionAndGet(
            Jsoup.connect(SUB_URL)
                .userAgent(Constants.USER_AGENT)
                .data("view", "server_configuration")
                .data("SUBID", subId)
                .timeout(TIMEOUT));
        Result result = extractResult(document.select("td.content_main").text());
        if (result != Result.SUCCESSFUL) {
            map.put("result", result.name());
        }
        Elements elements = document.select("input");
        for (Element el : elements) {
            map.put(el.attr("name"), el.attr("value"));
        }
        return map;
    }

    /**
     * Retrieves information about the packages or modifications installed to the <code>server</code> that can be managed from the
     * provider service, including game updates.
     *
     * @param subId the internal id GameServers uses for its servers
     * @return a map with mods (updates) key-values found in the GS web page
     * @throws IOException
     */
    @Retryable(backoff = @Backoff(2000L))
    public Map<String, String> getServerMods(String subId) throws IOException {
        rateLimiter.acquire();
        Map<String, String> map = new HashMap<>();
        Document document = validateSessionAndGet(
            Jsoup.connect(SUB_URL)
                .userAgent(Constants.USER_AGENT)
                .data("view", "server_mods")
                .data("SUBID", subId)
                .cookies(session));
        Elements mods = document.select("td.section_tabs table[style=\"margin-top: 10px;\"] tr");
        mods.stream().skip(1).filter(el -> {
            Elements cols = el.children();
            return cols.size() == 3 && cols.get(0).text().equals("Server Update");
        }).forEach(el -> map.put("latest-update", extractDate(el.children().get(1).text())));
        log.debug("Latest available update: {}", map.getOrDefault("latest-update", "Not found!"));
        Elements history = document.select("span.page_subtitle + table tr");
        history.stream().skip(1).findFirst().ifPresent(el -> {
            Elements cols = el.children();
            if (cols.size() == 3) {
                String date = cols.get(0).text();
                String author = cols.get(1).text();
                String mod = cols.get(2).text();
                map.put("last-mod-date", date);
                map.put("last-mod-by", author);
                map.put("last-mod-type", mod);
                log.debug("Most recent update: {} @ {} by {}", mod, date, author);
            } else {
                log.warn("Invalid mod history row, must be size 3 (found {})", cols.size());
            }
        });
        return map;
    }

    /**
     * Sends a restart instruction to the server.
     *
     * @param subId the internal id GameServers uses for its servers
     * @return <code>true</code> if the instruction was sent, <code>false</code> otherwise, regardless of the restart being
     * successful or not.
     * @throws IOException
     */
    public Result restart(String subId) throws IOException {
        rateLimiter.acquire();
        Document document = validateSessionAndGet(
            Jsoup.connect(SUB_URL)
                .userAgent(Constants.USER_AGENT)
                .data("SUBID", subId)
                .data("function", "restart")
                .timeout(TIMEOUT * 6));
        Result result = extractResult(document.text());
        if (result == Result.SUCCESSFUL) {
            log.info("*** Server RESTART in progress: {} --", subId);
            //log.debug("Response: {}", document.select("table.global").text());
        } else {
            log.warn("Could not restart server {}: {}", subId, result);
        }
        return result;
    }

    /**
     * Sends a shutdown instruction to the server.
     *
     * @param subId the internal id GameServers uses for its servers
     * @return <code>true</code> if the instruction was sent, <code>false</code> otherwise, regardless of the shutdown being
     * successful or not.
     * @throws IOException
     */
    public Result stop(String subId) throws IOException {
        rateLimiter.acquire();
        Document document = validateSessionAndGet(
            Jsoup.connect(SUB_URL)
                .userAgent(Constants.USER_AGENT)
                .data("SUBID", subId)
                .data("function", "stop")
                .timeout(TIMEOUT * 6));
        Result result = extractResult(document.text());
        if (result == Result.SUCCESSFUL) {
            log.info("*** Server STOP in progress: {} ***", subId);
            //log.debug("Response: {}", document.select("table.global").text());
        } else {
            log.warn("Could not stop server {}: {}", subId, result);
        }
        return result;
    }

    /**
     * Instructs the server to retrieve the latest game update.
     *
     * @param subId the internal id GameServers uses for its servers
     * @return <code>true</code> if the instruction was sent, <code>false</code> otherwise, regardless of the upgrade being
     * successful or not.
     * @throws IOException
     */
    public Result upgrade(String subId) throws IOException {
        rateLimiter.acquire();
        Document document = validateSessionAndGet(Jsoup.connect(SUB_URL)
            .userAgent(Constants.USER_AGENT)
            .data("view", "server_mods")
            .data("SUBID", subId)
            .data("function", "addmod")
            .data("modid", "730")
            .timeout(TIMEOUT * 6));
        String response = document.select("td.content_main").text();
        Result result = extractResult(response);
        if (result == Result.SUCCESSFUL) {
            log.info("*** Server UPGRADE in progress: {} ***", subId);
            //log.debug("Response: {}", response);
        } else {
            log.warn("Could not upgrade server {}: {}", subId, result);
        }
        return result;
    }

    public Result installMod(String subId, String modId) throws IOException {
        rateLimiter.acquire();
        Document document = validateSessionAndGet(Jsoup.connect(SUB_URL)
            .userAgent(Constants.USER_AGENT)
            .data("view", "server_mods")
            .data("SUBID", subId)
            .data("function", "addmod2")
            .data("MODID", modId)
            .timeout(TIMEOUT * 6));
        String response = document.select("td.content_main").text();
        Result result = extractResult(response);
        if (result == Result.SUCCESSFUL) {
            log.info("*** Server MOD {} INSTALL in progress: {} ***", modId, subId);
            //log.debug("Response: {}", document.select("td.content_main").text());
        } else {
            log.warn("Could not install mod to server {}: {}", subId, result);
        }
        return result;
    }

    /*
     * Utility methods
     */

    private Result extractResult(String response) {
        if (response.contains("Please wait")) {
            // This mod has been recently installed to your server. Please wait awhile before attempting another install
            return Result.TOO_MANY_INSTALLS;
        } else if (response.contains("Server is currently offline")) {
            // Server is currently offline and this action cannot be performed
            return Result.SERVER_OFFLINE;
        } else if (response.contains("We have detected an outage")) {
            /* We have detected an outage with your server and are currently working to resolve this issue.
            During this time, configuration management is disabled. Server crashes are generally solved within
            30 minutes and extended mass outages will be reported via the members area alert system.
             */
            return Result.OUTAGE_DETECTED;
        } else {
            return Result.SUCCESSFUL; // or a result we don't know
        }
    }

    private synchronized boolean login() throws IOException {
        rateLimiter.acquire();
        log.info("Authenticating to GS admin panel");
        String username = leagueProperties.getGameServers().getUsername();
        String password = leagueProperties.getGameServers().getPassword();
        Connection.Response loginForm = Jsoup.connect("https://my.gameservers.com/")
            .method(Connection.Method.GET)
            .userAgent(Constants.USER_AGENT)
            .execute();
        Document document = Jsoup.connect("https://my.gameservers.com/")
            .userAgent(Constants.USER_AGENT)
            .data("logout", "1")
            .data("username", username)
            .data("password", password)
            .data("query_string", "")
            .cookies(loginForm.cookies())
            .post();
        session.clear();
        session.putAll(loginForm.cookies());
        return !document.title().contains("Login");
    }

    private boolean isLoginPage(Document document) {
        return document.title().contains("Login");
    }

    @Retryable(include = {IOException.class}, backoff = @Backoff(2000L))
    private Document validateSessionAndGet(Connection connection) throws IOException {
        if (session.isEmpty()) {
            if (!login()) {
                // failed login at this point most likely means incorrect credentials
                throw new IOException("Login could not be completed");
            }
        }
        // our session might have expired
        Document document = connection.cookies(session).get();
        if (isLoginPage(document)) {
            session.clear();
            throw new IOException("Remote session has expired"); // but will retry
        }
        return document;
    }

    private String extractDate(String data) {
        String match = "(Last Updated ";
        if (!data.contains(match)) {
            return "Invalid string!";
        }
        return data.substring(data.indexOf(match) + match.length(), data.length() - 1);
    }

    public Map<String, String> getFTPConnectInfo(String subId) {
        Map<String, String> connectInfo = new HashMap<>();
        // FTP data can be also retrieved using getServerInfo, but it's too slow
        connectInfo.put("ftp-username", leagueProperties.getGameServers().getUsername());
        connectInfo.put("ftp-password", leagueProperties.getGameServers().getPassword());
        return connectInfo;
    }

    public enum Result {
        SUCCESSFUL, SERVER_OFFLINE, OUTAGE_DETECTED, TOO_MANY_INSTALLS
    }

}
