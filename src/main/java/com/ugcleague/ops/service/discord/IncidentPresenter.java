package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.Incident;
import com.ugcleague.ops.service.IncidentService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
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
import java.util.List;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;

@Service
@Transactional
public class IncidentPresenter {

    private static final Logger log = LoggerFactory.getLogger(IncidentPresenter.class);

    private final IncidentService incidentService;
    private final CommandService commandService;

    private OptionSpec<String> incidentsNonOptionSpec;
    private OptionSpec<Void> incidentsAllSpec;

    @Autowired
    public IncidentPresenter(IncidentService incidentService, CommandService commandService) {
        this.incidentService = incidentService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = newParser();
        incidentsNonOptionSpec = parser.nonOptions("Filter by incident group");
        incidentsAllSpec = parser.accepts("all", "Display all incidents");
        commandService.register(CommandBuilder.anyMatch(".incidents")
            .description("Configure and obtain incidents")
            .master()
            .originReplies()
            .parser(parser)
            .optionAliases(newAliasesMap(parser))
            .command(this::incidents)
            .build());
    }

    private String incidents(IMessage message, OptionSet optionSet) {
        // .incidents all
        if (optionSet.has(incidentsAllSpec)) {
            List<Incident> incidents = incidentService.getAllIncidents();
            return incidents.stream().map(Incident::toString).collect(Collectors.joining("\n"));
        }

        return "";
    }
}
