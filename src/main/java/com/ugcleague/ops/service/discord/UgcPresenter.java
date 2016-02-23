package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.SizzStats;
import com.ugcleague.ops.domain.document.UgcPlayer;
import com.ugcleague.ops.domain.document.UgcResult;
import com.ugcleague.ops.domain.document.UgcTeam;
import com.ugcleague.ops.service.GameStatsService;
import com.ugcleague.ops.service.UgcDataService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.SteamIdConverter;
import com.ugcleague.ops.web.rest.SizzMatch;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;

/**
 * Commands to handle UGC cached data.
 * <ul>
 * <li>ugc results</li>
 * </ul>
 */
@Service
@Transactional
public class UgcPresenter {

    private static final Logger log = LoggerFactory.getLogger(UgcPresenter.class);

    private final CommandService commandService;
    private final UgcDataService ugcDataService;
    private final GameStatsService statsService;
    private final Map<String, String> divisionAliasMap = new LinkedHashMap<>();

    private OptionSpec<Integer> resultsSeasonSpec;
    private OptionSpec<Integer> resultsWeekSpec;
    private OptionSpec<Boolean> resultsRefreshSpec;
    private OptionSpec<String> resultsNonOptionSpec;
    private OptionSpec<String> xrefResultsSpec;
    private OptionSpec<String> xrefNonOptionSpec;
    private OptionSpec<String> xrefDivisionSpec;
    private OptionSpec<Boolean> xrefRefreshSpec;
    private OptionSpec<Integer> xrefDepthSpec;
    private OptionSpec<Integer> xrefMatchesSpec;
    private OptionSpec<Integer> xrefDaysSpec;
    private OptionSpec<String> xrefFormatSpec;

    @Autowired
    public UgcPresenter(CommandService commandService, UgcDataService ugcDataService, GameStatsService statsService) {
        this.commandService = commandService;
        this.ugcDataService = ugcDataService;
        this.statsService = statsService;
    }

    @PostConstruct
    private void configure() {
        initDivisionAliasMap();
        initResultsCommand();
        initCrossReferenceCommand();
    }

    private void initDivisionAliasMap() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("NA Platinum", Arrays.asList("plat", "platinum", "naplat", "platna", "platinumna"));
        aliases.put("NA Gold", Arrays.asList("gold", "nagold", "goldna"));
        aliases.put("NA Silver", Arrays.asList("silver", "nasilver", "silverna"));
        aliases.put("NA Steel", Arrays.asList("steel", "nasteel", "steelna"));
        aliases.put("NA Iron", Arrays.asList("iron", "nairon", "ironna"));
        aliases.put("Euro Platinum", Arrays.asList("plateu", "euplat", "europlat", "plateuro", "platinumeu", "platinumeuro"));
        aliases.put("Euro Gold", Arrays.asList("goldeu", "eugold", "eurogold", "goldeuro"));
        aliases.put("Euro Silver", Arrays.asList("silvereu", "eusilver", "eurosilver", "silvereuro"));
        aliases.put("Euro Steel", Arrays.asList("steeleu", "eusteel", "eurosteel", "steeleuro"));
        aliases.put("Euro Iron", Arrays.asList("ironeu", "euiron", "euroiron", "ironeuro"));
        aliases.put("S. American", Arrays.asList("sa", "sam", "samerican", "samerica"));
        aliases.put("AUS/NZ Platinum", Arrays.asList("ausnzplat", "platausnz", "ausplat", "plataus",
            "ausnzplatinum", "platinumausnz", "ausplatinum", "platinumaus"));
        aliases.put("AUS NZ", Arrays.asList("aus", "ausnz", "ausnzsteel", "steelausnz", "aussteel", "steelaus"));
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            for (String key : entry.getValue()) {
                divisionAliasMap.put(key, entry.getKey());
            }
        }
    }

    private void initCrossReferenceCommand() {
        // .stats ugc <seasonweek> [div <name>]
        OptionParser parser = newParser();
        xrefNonOptionSpec = parser.nonOptions("Specify the UGC season-week results to cross-reference").ofType(String.class);
        xrefResultsSpec = parser.acceptsAll(asList("u", "ugc", "r", "results"), "Specify the UGC season-week results to cross-reference")
            .withRequiredArg().withValuesSeparatedBy(",");
        xrefDivisionSpec = parser.acceptsAll(asList("d", "div", "division"), "Filter by division name")
            .withRequiredArg().withValuesSeparatedBy(",");
        xrefRefreshSpec = parser.accepts("refresh", "Force a cache refresh").withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        xrefDepthSpec = parser.accepts("depth", "How many matches to look for roster matches in each player's history")
            .withRequiredArg().ofType(Integer.class).defaultsTo(50);
        xrefMatchesSpec = parser.accepts("matches", "How many roster matches to look for in each match")
            .withRequiredArg().ofType(Integer.class).defaultsTo(7); // should be Format-2
        xrefDaysSpec = parser.accepts("days", "For how many days after schedule date should we look for logs")
            .withRequiredArg().ofType(Integer.class).defaultsTo(10);
        xrefFormatSpec = parser.accepts("format", "Game format, to filter by player count")
            .withRequiredArg().defaultsTo("HL");
        Map<String, String> aliases = new HashMap<>();
        aliases.put("ugc", "--ugc");
        aliases.put("results", "--ugc");
        aliases.put("div", "--div");
        aliases.put("division", "--div");
        aliases.put("refresh", "--refresh");
        aliases.put("depth", "--depth");
        aliases.put("matches", "--matches");
        aliases.put("days", "--days");
        commandService.register(CommandBuilder.startsWith(".stats")
            .description("Cross-references UGC matches against Sizzling/LogsTF stats")
            .support().originReplies().parser(parser).withOptionAliases(aliases).queued().command(this::xref).build());
    }

    private String xref(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        // first, grab the matches
        List<String> nonOptions = optionSet.valuesOf(xrefNonOptionSpec);
        if (!optionSet.has(xrefResultsSpec) && nonOptions.isEmpty()) {
            return "You must add a results season-week in the format: `s18w5`";
        }

        Map<SeasonWeek, Set<UgcResult>> resultMap = new LinkedHashMap<>();
        boolean refresh = optionSet.has(xrefRefreshSpec) ? optionSet.valueOf(xrefRefreshSpec) : false;
        for (String sw : optionSet.valuesOf(xrefResultsSpec)) {
            if (sw.toLowerCase().matches("^s[0-9]+w[0-9]+$")) {
                String[] seasonWeek = sw.split("s|w"); // would return ["", season, week]
                SeasonWeek key = new SeasonWeek(Integer.parseInt(seasonWeek[1]), Integer.parseInt(seasonWeek[2]));
                if (!resultMap.containsKey(key)) {
                    log.debug("Finding UGC match result data for {}", key);
                    resultMap.put(key, ugcDataService.findBySeasonAndWeek(key.season, key.week, refresh));
                }
            }
        }

        int minMatches = optionSet.valueOf(xrefMatchesSpec);

        if (optionSet.has(xrefFormatSpec)) {
            String format = optionSet.valueOf(xrefFormatSpec).toLowerCase();
            switch (format) {
                case "hl":
                case "9v9":
                    minMatches = 7;
                    break;
                case "6v6":
                case "6s":
                    minMatches = 4;
                    break;
                case "4v4":
                case "4s":
                    minMatches = 3;
                    break;
                default:
            }
        }

        String division = null;
        if (optionSet.has(xrefDivisionSpec)) {
            division = divisionAliasMap.get(optionSet.valueOf(xrefDivisionSpec)); // can return null
        }

        // collect stats within the possible match times for each result until a match is found
        // time frame: [scheduleDate, scheduleDate + 10 days]
        // extra filters: stats player count, map name
        StringBuilder response = new StringBuilder();
        for (Set<UgcResult> results : resultMap.values()) {
            for (UgcResult result : results) {
                log.debug("Preparing to search for UGC match #{}: {} ({}) vs {} ({}) @ {}",
                    result.getMatchId(), result.getHomeTeam(), result.getHomeClanId(),
                    result.getAwayTeam(), result.getAwayClanId(), result.getMapName());
                ZonedDateTime start = result.getScheduleDate();
                ZonedDateTime end = result.getScheduleDate().plusDays(optionSet.valueOf(xrefDaysSpec));
                Optional<UgcTeam> home = ugcDataService.findTeamById(result.getHomeClanId(), refresh);
                if (!home.isPresent()) {
                    response.append("Could not find team ").append(result.getHomeTeam())
                        .append(" with id: ").append(result.getHomeClanId()).append("\n");
                    break;
                }
                Optional<UgcTeam> away = ugcDataService.findTeamById(result.getAwayClanId(), refresh);
                if (!away.isPresent()) {
                    response.append("Could not find team ").append(result.getAwayTeam())
                        .append(" with id: ").append(result.getAwayClanId()).append("\n");
                    break;
                }
                if (division != null && !home.get().getDivisionName().equals(division)
                    && !away.get().getDivisionName().equals(division)) {
                    // skip due to division filter
                    continue;
                }
                LookupSettings settings = new LookupSettings(start, end,
                    optionSet.valueOf(xrefDepthSpec), minMatches, result.getMapName());
                List<MatchInfo> matches = findStats(home.get(), away.get(), settings);
                if (!matches.isEmpty()) {
                    response.append(String.format("[#%d] %s vs %s @ %s\n", result.getMatchId(),
                        result.getHomeTeam(), result.getAwayTeam(), result.getMapName()));
                    for (MatchInfo match : matches) {
                        response.append("\t\t").append(match.homeTeamMatches + match.awayTeamMatches)
                            .append(" roster matches found: ").append("http://sizzlingstats.com/stats/")
                            .append(match.stats.getId()).append("\n");
                    }
                }
            }
        }

        return response.toString();
    }

    private List<MatchInfo> findStats(UgcTeam home, UgcTeam away, LookupSettings settings) {
        List<MatchInfo> matches = new ArrayList<>();
        List<UgcPlayer> players = new ArrayList<>();
        players.addAll(home.getRoster());
        players.addAll(away.getRoster());
        Set<Integer> sizzlingScanned = new LinkedHashSet<>();
        for (UgcPlayer player : players) {
            log.info("SizzStats: Finding last games for player {} ({})", player.getName(), player.getId());
            int count = 0;
            // define a period of time where the match is most likely to have been recorded
            ZonedDateTime startDate = settings.startDate;
            ZonedDateTime endDate = settings.endDate;
            for (SizzMatch match : statsService.getSizzMatchIterable(player.getId())) {
                int id = match.getId();
                ZonedDateTime created = parseMatchDate(match.getCreated());
                // ignore live matches
                // ignore matches newer than the endDate
                if (match.isLive() || created.isAfter(endDate)) {
                    continue;
                }
                // abort when finding a match older than startDate
                if (created.isBefore(startDate)) {
                    log.info("SizzStats: Reached start date for player {}", player.getId());
                    break;
                }
                // don't check the same match twice
                if (sizzlingScanned.contains(id)) {
                    count++;
                    continue;
                }
                sizzlingScanned.add(id);
                // don't check too many stats per player
                if (count >= settings.matchDepthPerPlayer) {
                    log.info("SizzStats: Maximum depth reached for player {}", player.getId());
                    break;
                }
                log.info("SizzStats: Retrieving match stats #{}", id);
                SizzStats stats = statsService.getSizzStats(id);
                if (stats == null) {
                    log.warn("SizzStats: No stats for id {}", id);
                    continue;
                }
                if (stats.getMap() != null && stats.getMap().equals(settings.mapName)) {
                    continue;
                }
                int homeRosterMatched = 0;
                int awayRosterMatched = 0;
                // Steam3 -> Player
                Map<String, SizzStats.Player> participants = stats.getPlayers();
                for (UgcPlayer toMatch : home.getRoster()) {
                    String key = SteamIdConverter.steamId64To3(toMatch.getId());
                    if (participants.containsKey(key)) {
                        ++homeRosterMatched;
                    }
                }
                for (UgcPlayer toMatch : away.getRoster()) {
                    String key = SteamIdConverter.steamId64To3(toMatch.getId());
                    if (participants.containsKey(key)) {
                        ++awayRosterMatched;
                    }
                }
                if (homeRosterMatched >= settings.minimumRosterMatches
                    && awayRosterMatched >= settings.minimumRosterMatches) {
                    log.info("SizzStats: Matched stats with league result #{} ({} home, {} away)",
                        id, homeRosterMatched, awayRosterMatched);
                    matches.add(new MatchInfo(stats, homeRosterMatched, awayRosterMatched));
                } else {
                    log.debug("SizzStats: Not enough matched players #{} ({} home, {} away)",
                        id, homeRosterMatched, awayRosterMatched);
                }
                count++;
            }
        }
        return matches;
    }

    private ZonedDateTime parseMatchDate(String date) {
        // 2016-02-21T03:34:54.875Z
        return Instant.parse(date).atZone(ZoneId.systemDefault());
    }

    private void initResultsCommand() {
        // .ugc results [-s <season>] [-w <week>] [-r <refresh>]
        // .ugc results <season> <week>
        OptionParser parser = newParser();
        resultsNonOptionSpec = parser.nonOptions("If not using `-s` and `-w`, the first argument will be the season " +
            "number, and the second will be the week").ofType(String.class);
        resultsSeasonSpec = parser.acceptsAll(asList("s", "season"), "HL season number").withRequiredArg().ofType(Integer.class);
        resultsWeekSpec = parser.acceptsAll(asList("w", "week"), "HL week number").withRequiredArg().ofType(Integer.class);
        resultsRefreshSpec = parser.acceptsAll(asList("r", "refresh"), "forces a cache refresh").withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandService.register(CommandBuilder.startsWith(".ugc results")
            .description("Get HL match results for the given season/week").master()
            .parser(parser).command(this::results).queued().build());
    }

    private String results(IMessage message, OptionSet optionSet) {
        if (!optionSet.has("?")) {
            List<String> nonOptions = optionSet.valuesOf(resultsNonOptionSpec);
            int season;
            int week;
            if (!optionSet.has(resultsSeasonSpec)) {
                if (nonOptions.size() == 2 && nonOptions.get(0).matches("[0-9]+")) {
                    season = Integer.parseInt(nonOptions.get(0));
                } else {
                    return "Invalid call. Make sure you enter exactly 2 numeric arguments";
                }
            } else {
                season = optionSet.valueOf(resultsSeasonSpec);
            }
            if (!optionSet.has(resultsWeekSpec)) {
                if (nonOptions.size() == 2 && nonOptions.get(1).matches("[0-9]+")) {
                    week = Integer.parseInt(nonOptions.get(1));
                } else {
                    return "Invalid call. Make sure you enter exactly 2 numeric arguments";
                }
            } else {
                week = optionSet.valueOf(resultsWeekSpec);
            }
            boolean refresh = optionSet.has(resultsRefreshSpec) ? optionSet.valueOf(resultsRefreshSpec) : false;
            Set<UgcResult> resultSet = ugcDataService.findBySeasonAndWeek(season, week, refresh); // never null, can be empty
            return "Cached " + resultSet.size() + " results";
        }
        return null;
    }

    private static class MatchInfo {
        private final SizzStats stats;
        private final int homeTeamMatches;
        private final int awayTeamMatches;

        private MatchInfo(SizzStats stats, int homeTeamMatches, int awayTeamMatches) {
            this.stats = stats;
            this.homeTeamMatches = homeTeamMatches;
            this.awayTeamMatches = awayTeamMatches;
        }
    }

    private static class LookupSettings {
        private final ZonedDateTime startDate;
        private final ZonedDateTime endDate;
        private final int matchDepthPerPlayer;
        private final int minimumRosterMatches;
        private final String mapName;

        private LookupSettings(ZonedDateTime startDate, ZonedDateTime endDate, int matchDepthPerPlayer,
                               int minimumRosterMatches, String mapName) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.matchDepthPerPlayer = matchDepthPerPlayer;
            this.minimumRosterMatches = minimumRosterMatches;
            this.mapName = mapName;
        }
    }

    private static class SeasonWeek {
        final int season;
        final int week;

        SeasonWeek(int season, int week) {
            this.season = season;
            this.week = week;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SeasonWeek that = (SeasonWeek) o;
            return season == that.season &&
                week == that.week;
        }

        @Override
        public int hashCode() {
            return Objects.hash(season, week);
        }

        @Override
        public String toString() {
            return "s" + season + "w" + week;
        }
    }
}
