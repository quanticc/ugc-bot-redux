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
import java.util.Set;

import static java.util.Arrays.asList;

@Service
@Transactional
public class UgcQueryService {

    private final CommandService commandService;
    private final UgcDataService ugcDataService;

    private OptionSpec<Integer> resultsSeasonSpec;
    private OptionSpec<Integer> resultsWeekSpec;
    private OptionSpec<Boolean> resultsRefreshSpec;

    @Autowired
    public UgcQueryService(CommandService commandService, UgcDataService ugcDataService) {
        this.commandService = commandService;
        this.ugcDataService = ugcDataService;
    }

    @PostConstruct
    private void configure() {
        initResultsCommand();
    }

    private void initResultsCommand() {
        // .ugc results [-s <season>] [-w <week>] [-r <refresh>]
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        resultsSeasonSpec = parser.acceptsAll(asList("s", "season"), "HL season number").withRequiredArg().ofType(Integer.class).required();
        resultsWeekSpec = parser.acceptsAll(asList("w", "week"), "HL week number").withRequiredArg().ofType(Integer.class).required();
        resultsRefreshSpec = parser.acceptsAll(asList("r", "refresh"), "forces a cache refresh").withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandService.register(CommandBuilder.startsWith(".ugc results")
            .description("Get HL match results for the given season/week [A]").permission("support")
            .parser(parser).command(this::results).queued().build());
    }

    private String results(IMessage message, OptionSet optionSet) {
        if (!optionSet.has("?")) {
            int season = optionSet.valueOf(resultsSeasonSpec);
            int week = optionSet.valueOf(resultsWeekSpec);
            boolean refresh = optionSet.has(resultsRefreshSpec) ? optionSet.valueOf(resultsRefreshSpec) : false;
            Set<UgcResult> resultSet = ugcDataService.findBySeasonAndWeek(season, week, refresh); // never null, can be empty
            return "Cached " + resultSet.size() + " results";
        }
        return null;
    }
}
