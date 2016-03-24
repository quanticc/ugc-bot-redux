package com.ugcleague.ops.service.discord;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.steam.community.SteamId;
import com.ugcleague.ops.service.UgcDataService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.RosterData;
import com.ugcleague.ops.web.rest.UgcPlayerPage;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.repeat;
import static org.apache.commons.lang3.StringUtils.rightPad;

@Service
public class RosterPresenter {

    private static final Logger log = LoggerFactory.getLogger(RosterPresenter.class);

    private final Pattern STATUS = Pattern.compile("^.+\"(.+)\"\\s+(\\[([a-zA-Z]):([0-5]):([0-9]+)(:[0-9]+)?\\])\\s+.*$", Pattern.MULTILINE);
    private final Pattern LOGLINE = Pattern.compile("^.*\"(.+)<([0-9]+)><(\\[([a-zA-Z]):([0-5]):([0-9]+)(:[0-9]+)?\\])>.*$", Pattern.MULTILINE);
    private final Pattern STANDALONE = Pattern.compile("(\\[U:([0-5]):([0-9]+)(:[0-9]+)?\\])", Pattern.MULTILINE);
    private final Map<String, String> formatConverter = new HashMap<>();

    private final CommandService commandService;
    private final UgcDataService ugcDataService;

    @Autowired
    public RosterPresenter(CommandService commandService, UgcDataService ugcDataService) {
        this.commandService = commandService;
        this.ugcDataService = ugcDataService;
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.startsWith(".check")
            .description("Check UGC rosters from a status command output").unrestricted().originReplies()
            .queued().noParser().command(this::checkRosters).build());
        formatConverter.put("TF2 Highlander", "9v9");
        formatConverter.put("TF2 6vs6", "6v6");
        formatConverter.put("TF2 4vs4", "4v4");
    }

    private String checkRosters(IMessage message, OptionSet optionSet) {
        String data = message.getContent().split(" ", 2)[1];
        Matcher statusMatcher = STATUS.matcher(data);
        Matcher logMatcher = LOGLINE.matcher(data);
        Matcher standaloneMatcher = STANDALONE.matcher(data);
        StringBuilder builder = new StringBuilder("*Roster check results*\n```\n");
        Set<RosterData> players = new LinkedHashSet<>();
        while (statusMatcher.find()) {
            RosterData player = new RosterData();
            player.setServerName(statusMatcher.group(1));
            player.setModernId(statusMatcher.group(2));
            try {
                player.setCommunityId(SteamId.convertSteamIdToCommunityId(player.getModernId()));
            } catch (SteamCondenserException e) {
                log.warn("Invalid id {}: {}", player.getModernId(), e.toString());
            }
            if (!players.contains(player)) {
                log.debug("Matched by status: {}", player);
                players.add(player);
            }
        }
        while (logMatcher.find()) {
            RosterData player = new RosterData();
            player.setServerName(statusMatcher.group(1));
            player.setModernId(statusMatcher.group(3));
            try {
                player.setCommunityId(SteamId.convertSteamIdToCommunityId(player.getModernId()));
            } catch (SteamCondenserException e) {
                log.warn("Invalid id {}: {}", player.getModernId(), e.toString());
            }
            if (!players.contains(player)) {
                log.debug("Matched by log: {}", player);
                players.add(player);
            }
        }
        while (standaloneMatcher.find()) {
            RosterData player = new RosterData();
            player.setModernId(statusMatcher.group(1));
            try {
                player.setCommunityId(SteamId.convertSteamIdToCommunityId(player.getModernId()));
                SteamId steamId = SteamId.create(player.getCommunityId());
                if (needsFetching(steamId)) {
                    steamId.fetchData();
                }
                player.setServerName(steamId.getNickname());
            } catch (SteamCondenserException e) {
                log.warn("Invalid id {}: {}", player.getModernId(), e.toString());
            }
            if (!players.contains(player)) {
                log.debug("Matched by Steam3ID: {}", player);
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return "No player data or Steam3IDs found";
        }
        String filter = "";
        if (data.startsWith("HL ") || data.startsWith("hl ") || data.startsWith("9 ") || data.startsWith("9v ") || data.startsWith("9v9 ")) {
            filter = "9v9";
        } else if (data.startsWith("6s ") || data.startsWith("6 ") || data.startsWith("6v ") || data.startsWith("6v6 ")) {
            filter = "6v6";
        } else if (data.startsWith("4s ") || data.startsWith("4 ") || data.startsWith("4v ") || data.startsWith("4v4 ")) {
            filter = "4v4";
        }
        List<RosterData> result = ugcDataService.findPlayers(players);
        int nameWidth = result.stream().map(d -> d.getServerName().length()).reduce(0, Integer::max) + 2;
        int teamWidth = result.stream().filter(d -> d.getUgcData() != null && d.getUgcData().getTeam() != null)
            .flatMap(d -> d.getUgcData().getTeam().stream())
            .map(t -> (t.getClanId() + " ").length() + t.getName().length()).reduce(0, Integer::max) + 2;
        int divWidth = result.stream().filter(d -> d.getUgcData() != null && d.getUgcData().getTeam() != null)
            .flatMap(d -> d.getUgcData().getTeam().stream())
            .map(t -> t.getDivision().length()).reduce(0, Integer::max) + 2;
        int formatWidth = "9v9".length() + 2;
        builder.append(rightPad("Name", nameWidth)).append(rightPad("Team", teamWidth))
            .append(rightPad("Division", divWidth)).append(rightPad("Mode", formatWidth))
            .append("\n")
            .append(repeat('-', nameWidth + teamWidth + divWidth + formatWidth)).append("\n");
        StringBuilder recentJoinsBuilder = new StringBuilder();
        Set<Integer> teamIds = new LinkedHashSet<>();
        for (RosterData player : result) {
            if (Thread.interrupted()) {
                log.warn("Roster check interrupted");
                return "";
            }
            if (player.getUgcData() == null || player.getUgcData().getTeam() == null || player.getUgcData().getTeam().isEmpty()) {
                builder.append(rightPad(player.getServerName(), nameWidth)).append("\n");
            } else {
                boolean first = true;
                for (UgcPlayerPage.Team team : player.getUgcData().getTeam()) {
                    // convert if legacy format
                    if (team.getClanId() > 0) {
                        // format needs conversion
                        team.setFormat(formatConverter.getOrDefault(team.getFormat(), team.getFormat()));
                        teamIds.add(team.getClanId());
                    } else {
                        team.setJoined(player.getUgcData().getJoined());
                    }
                    if (filter.isEmpty() || team.getFormat().equals(filter)) {
                        builder.append(rightPad(first ? player.getServerName() : "", nameWidth))
                            .append(rightPad((team.getClanId() > 0 ? team.getClanId() + " " : "") + team.getName(), teamWidth))
                            .append(rightPad(team.getDivision(), divWidth))
                            .append(rightPad(team.getFormat().equals("9v9") ? "HL" : team.getFormat(), formatWidth))
                            .append("\n");
                        first = false;
                        if (isRecentJoin(team)) {
                            recentJoinsBuilder.append("\n*Warning* ")
                                .append(player.getServerName())
                                .append(" joined ").append(team.getName())
                                .append(" less than 18 hours ago!");
                        }
                    }
                }
            }
        }
        if (teamIds.size() > 0 && teamIds.size() <= 2) {
            recentJoinsBuilder.append('\n');
            for (Integer id : teamIds) {
                recentJoinsBuilder.append("http://www.ugcleague.com/team_page.cfm?clan_id=").append(id).append("\n");
            }
        }
        return builder.append("```").append(recentJoinsBuilder.toString()).toString();
    }

    private boolean needsFetching(SteamId id) {
        // fetch max once per hour
        return !id.isFetched() || Instant.ofEpochMilli(id.getFetchTime()).isBefore(Instant.now().minusSeconds(3600));
    }

    private boolean isRecentJoin(UgcPlayerPage.Team team) {
        String pattern = team.getClanId() > 0 ? "MMMM, dd yyyy HH:mm:ss" : "yyyy-MM-dd HH:mm:ss";
        if (team.getJoined() == null) {
            return false;
        }
        LocalDateTime date = LocalDateTime.parse(team.getJoined(), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
        ZonedDateTime join = date.atZone(ZoneId.of("America/New_York"));
        return ZonedDateTime.now().minusHours(18).isBefore(join);
    }
}
