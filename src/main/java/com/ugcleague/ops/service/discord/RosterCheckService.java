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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.repeat;
import static org.apache.commons.lang3.StringUtils.rightPad;

@Service
public class RosterCheckService {

    private static final Logger log = LoggerFactory.getLogger(RosterCheckService.class);
    private final Pattern INDIVIDUAL_PATTERN = Pattern.compile("^.+\"(.+)\"\\s+(\\[([a-zA-Z]):([0-5]):([0-9]+)(:[0-9]+)?\\])\\s+.*$", Pattern.MULTILINE);

    private final CommandService commandService;
    private final UgcDataService ugcDataService;

    @Autowired
    public RosterCheckService(CommandService commandService, UgcDataService ugcDataService) {
        this.commandService = commandService;
        this.ugcDataService = ugcDataService;
    }

    @PostConstruct
    private void configure() {
        commandService.register(CommandBuilder.startsWith(".check")
            .description("Check UGC rosters from a status command output").unrestricted().originReplies().mention()
            .queued().noParser().command(this::checkRosters).build());
    }

    private String checkRosters(IMessage message, OptionSet optionSet) {
        String data = message.getContent().split(" ", 2)[1];
        Matcher matcher = INDIVIDUAL_PATTERN.matcher(data);
        StringBuilder builder = new StringBuilder("*Roster check results*\n```\n");
        List<RosterData> players = new ArrayList<>();
        while (matcher.find()) {
            RosterData player = new RosterData();
            player.setServerName(matcher.group(1));
            player.setModernId(matcher.group(2));
            try {
                player.setCommunityId(SteamId.convertSteamIdToCommunityId(player.getModernId()));
            } catch (SteamCondenserException e) {
                log.warn("Invalid id {}: {}", player.getModernId(), e.toString());
            }
            players.add(player);
        }
        if (players.isEmpty()) {
            return "No `status` command player data found";
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
            .map(t -> t.getName().length()).reduce(0, Integer::max) + 2;
        int divWidth = result.stream().filter(d -> d.getUgcData() != null && d.getUgcData().getTeam() != null)
            .flatMap(d -> d.getUgcData().getTeam().stream())
            .map(t -> t.getDivision().length()).reduce(0, Integer::max) + 2;
        int formatWidth = "9v9".length() + 2;
        builder.append(rightPad("Name", nameWidth)).append(rightPad("Team", teamWidth))
            .append(rightPad("Division", divWidth)).append(rightPad("Mode", formatWidth))
            .append("\n")
            .append(repeat('-', nameWidth + teamWidth + divWidth + formatWidth)).append("\n");
        StringBuilder recentJoinsBuilder = new StringBuilder();
        for (RosterData player : result) {
            if (player.getUgcData() == null || player.getUgcData().getTeam() == null || player.getUgcData().getTeam().isEmpty()) {
                builder.append(rightPad(player.getServerName(), nameWidth)).append("\n");
            } else {
                boolean first = true;
                for (UgcPlayerPage.Team team : player.getUgcData().getTeam()) {
                    if (filter.isEmpty() || team.getFormat().equals(filter)) {
                        builder.append(rightPad(first ? player.getServerName() : "", nameWidth))
                            .append(rightPad(team.getName(), teamWidth))
                            .append(rightPad(team.getDivision(), divWidth))
                            .append(rightPad(team.getFormat().equals("9v9") ? "HL" : team.getFormat(), formatWidth))
                            .append("\n");
                        first = false;
                        if (isRecentJoin(team.getJoined())) {
                            recentJoinsBuilder.append("\n*Warning* ")
                                .append(player.getServerName())
                                .append(" joined ").append(team.getName())
                                .append(" less than 18 hours ago!");
                        }
                    }
                }
            }
        }
        return builder.append("```").append(recentJoinsBuilder.toString()).toString();
    }

    private boolean isRecentJoin(String ugcFormatDate) {
        LocalDateTime date = LocalDateTime.parse(ugcFormatDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH));
        ZonedDateTime join = date.atZone(ZoneId.of("America/New_York"));
        return ZonedDateTime.now().minusHours(18).isBefore(join);
    }
}
