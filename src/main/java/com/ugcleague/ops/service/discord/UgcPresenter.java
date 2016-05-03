package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.UgcResult;
import com.ugcleague.ops.service.UgcDataService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;

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

    private final CommandService commandService;
    private final UgcDataService ugcDataService;

    private OptionSpec<Integer> resultsSeasonSpec;
    private OptionSpec<Integer> resultsWeekSpec;
    private OptionSpec<Boolean> resultsRefreshSpec;
    private OptionSpec<String> resultsNonOptionSpec;

    @Autowired
    public UgcPresenter(CommandService commandService, UgcDataService ugcDataService) {
        this.commandService = commandService;
        this.ugcDataService = ugcDataService;
    }

    @PostConstruct
    private void configure() {
        initResultsCommand();
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
}
