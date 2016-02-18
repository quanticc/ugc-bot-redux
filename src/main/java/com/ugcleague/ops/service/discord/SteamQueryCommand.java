package com.ugcleague.ops.service.discord;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.exceptions.WebApiException;
import com.github.koraktor.steamcondenser.steam.community.SteamId;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static org.apache.commons.lang3.StringUtils.leftPad;

@Service
public class SteamQueryCommand {

    private static final Logger log = LoggerFactory.getLogger(SteamQueryCommand.class);
    private static final Pattern STEAM_URL = Pattern.compile("(https?:\\/\\/steamcommunity\\.com\\/)(id|profiles)\\/([\\w-]+)\\/?");

    private final CommandService commandService;

    private OptionSpec<String> steamNonOptionSpec;

    @Autowired
    public SteamQueryCommand(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = newParser();
        steamNonOptionSpec = parser.nonOptions("A single SteamID32, SteamID64 or Community URL of a Steam user").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".steam").description("Retrieves info about a Steam user")
            .unrestricted().originReplies().queued().parser(parser).command(this::executeSteamQuery).build());
    }

    private String executeSteamQuery(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(steamNonOptionSpec);
        if (optionSet.has("?") || nonOptions.isEmpty()) {
            return null;
        }
        String key = nonOptions.get(0);
        long steamId64 = anyToSteamId64(key); // can be 0 (failed)
        if (steamId64 == 0) {
            return "Could not found user or the Steam Community servers are down";
        }
        String steamId32 = "?";
        String steam3Id = "?";
        try {
            steamId32 = SteamId.convertCommunityIdToSteamId(steamId64);
            steam3Id = steam2To3(steamId32);
        } catch (SteamCondenserException e) {
            log.warn("Invalid community id: {}", e.toString());
        }
        StringBuilder builder = new StringBuilder("```\n");
        int pad = 11;
        try {
            SteamId steamId = SteamId.create(steamId64);
            if (needsFetching(steamId)) {
                log.debug("Fetching data for Steam id: {}", steamId64);
                steamId.fetchData();
            }
            builder.append(leftPad("steamName: ", pad)).append(steamId.getNickname()).append("\n")
                .append(leftPad("steam3ID: ", pad)).append(steam3Id).append("\n")
                .append(leftPad("steamID32: ", pad)).append(steamId32).append("\n")
                .append(leftPad("steamID64: ", pad)).append("http://steamcommunity.com/profiles/").append(steamId64).append("\n")
                //.append(leftPad("steamRep: ", pad)).append("http://steamrep.com/profiles/").append(steamId64).append("\n")
                .append(leftPad("Status: ", pad)).append(steamId.getStateMessage()).append("\n");
            if (!steamId.getPrivacyState().equals("public")) {
                builder.append("* Profile privacy is set to ").append(steamId.getPrivacyState()).append("\n");
            } else {
                builder.append(leftPad("Joined: ", pad)).append(steamId.getMemberSince().toInstant()).append("\n");
                    //.append(leftPad("customURL: ", pad)).append("/id/").append(steamId.getCustomUrl()).append("\n");
            }
            if (steamId.isBanned()) {
                builder.append("* User is VAC banned\n");
            }
            if (!steamId.getTradeBanState().equals("None")) {
                builder.append("* User has ").append(steamId.getTradeBanState()).append(" trade state\n");
            }
            builder.append("```\n").append("http://www.ugcleague.com/players_page.cfm?player_id=").append(steamId64);
            return builder.toString();
        } catch (SteamCondenserException e) {
            log.warn("Could not create steamId data for {}: {}", steamId64, e.toString());
        }

        builder.append(leftPad("steam3ID: ", pad)).append(steam3Id).append("\n")
            .append(leftPad("steamID32: ", pad)).append(steamId32).append("\n")
            .append(leftPad("steamID64: ", pad)).append("http://steamcommunity.com/profiles/").append(steamId64).append("\n")
            //.append(leftPad("steamRep: ", pad)).append("http://steamrep.com/profiles/").append(steamId64).append("\n")
            .append("```\n").append("http://www.ugcleague.com/players_page.cfm?player_id=").append(steamId64);
        return builder.toString();
    }

    private boolean needsFetching(SteamId id) {
        // fetch max once per hour
        return !id.isFetched() || Instant.ofEpochMilli(id.getFetchTime()).isBefore(Instant.now().minusSeconds(3600));
    }

    @Scheduled(cron = "0 0 6 * * *")
    void clearSteamIdCache() {
        log.debug("Clearing Steam ID cache");
        SteamId.clearCache(); // each day at 6am
    }

    private String steam2To3(String steamId32) {
        String[] tokens = steamId32.split(":");
        if (tokens.length != 3) {
            return "";
        }
        int universe = Integer.parseInt(tokens[0].replace("STEAM_", ""));
        if (universe == 0) {
            universe = 1;
        }
        int account = Integer.parseInt(tokens[1]) + (Integer.parseInt(tokens[2]) << 1);
        return "[U:" + universe + ":" + account + "]";
    }

    private long anyToSteamId64(String key) {
        try {
            if (key.matches("[0-9]+")) {
                return Long.parseLong(key);
            } else if (key.matches("^STEAM_[0-1]:[0-1]:[0-9]+$")) {
                String[] tmpId = key.substring(8).split(":");
                return Long.valueOf(tmpId[0]) + Long.valueOf(tmpId[1]) * 2 + 76561197960265728L;
            } else if (key.matches("^\\[U:[0-1]:[0-9]+\\]+$")) {
                String[] tmpId = key.substring(3, key.length() - 1).split(":");
                return Long.valueOf(tmpId[0]) + Long.valueOf(tmpId[1]) + 76561197960265727L;
            } else {
                Matcher matcher = STEAM_URL.matcher(key);
                if (matcher.matches()) {
                    String type = matcher.group(2);
                    String value = matcher.group(3);
                    if (type.equalsIgnoreCase("profiles")) {
                        return Long.parseLong(value);
                    } else if (type.equalsIgnoreCase("id")) {
                        return vanityToSteamId64(value);
                    }
                } else {
                    return vanityToSteamId64(key);
                }
            }
        } catch (NumberFormatException e) {
            log.debug("Could not format as numeric value: {}", e.toString());
        }
        return 0;
    }

    private long vanityToSteamId64(String vanity) {
        try {
            Long id64 = SteamId.resolveVanityUrl(vanity); // returns null on failure
            if (id64 != null) {
                return id64;
            }
        } catch (WebApiException e) {
            log.warn("Could not retrieve community data for vanity url: {}", e.toString());
        }
        return 0;
    }
}
