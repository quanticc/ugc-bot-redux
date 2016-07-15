package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.NuclearStream;
import com.ugcleague.ops.domain.document.Publisher;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.NuclearService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;

@Service
public class NuclearPresenter {

    private static final Logger log = LoggerFactory.getLogger(NuclearPresenter.class);

    private final DiscordService discordService;
    private final CommandService commandService;
    private final NuclearService nuclearService;

    private OptionSpec<String> createStreamSpec;
    private OptionSpec<String> createIdSpec;
    private OptionSpec<String> createNameSpec;
    private OptionSpec<String> startNonOptionSpec;
    private OptionSpec<String> stopNonOptionSpec;
    private OptionSpec<String> deleteNonOptionSpec;
    private OptionSpec<String> infoNonOptionSpec;

    @Autowired
    public NuclearPresenter(DiscordService discordService, CommandService commandService,
                            NuclearService nuclearService) {
        this.discordService = discordService;
        this.commandService = commandService;
        this.nuclearService = nuclearService;
    }

    @PostConstruct
    private void configure() {
        configureCreateCommand();
        configureStartCommand();
        configureStopCommand();
        configureDeleteCommand();
        configureRunInfoCommand();
    }

    private void configureRunInfoCommand() {
        // .nt info <pub name>
        OptionParser parser = newParser();
        infoNonOptionSpec = parser.nonOptions("Publisher name").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".nt info").unrestricted().originReplies()
            .description("Get info about a NT data stream").parser(parser).optionAliases(newAliasesMap(parser))
            .command((message, optionSet) -> {
                List<String> publishers = optionSet.valuesOf(infoNonOptionSpec);

                StringBuilder builder = new StringBuilder();
                for (String publisher : publishers) {
                    Optional<NuclearStream> stream = nuclearService.findStreamByPublisher(publisher);
                    if (stream.isPresent()) {
                        builder.append("**").append(publisher).append("**\n")
                            .append(nuclearService.getRunInfo(stream.get()));
                    }
                }
                return builder.toString();
            }).build());
    }

    private void configureDeleteCommand() {
        // .nt delete <pub name>
        OptionParser parser = newParser();
        deleteNonOptionSpec = parser.nonOptions("Publisher name").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".nt delete").unrestricted().originReplies()
            .description("Delete NT data stream").parser(parser).optionAliases(newAliasesMap(parser))
            .command((message, optionSet) -> {
                List<String> publishers = optionSet.valuesOf(deleteNonOptionSpec);

                StringBuilder builder = new StringBuilder();
                for (String publisher : publishers) {
                    Optional<NuclearStream> stream = nuclearService.findStreamByPublisher(publisher);
                    if (stream.isPresent()) {
                        nuclearService.delete(stream.get());
                        builder.append("Deleted **").append(publisher).append("** stream\n");
                    }
                }
                return builder.toString();
            }).build());
    }

    private void configureStopCommand() {
        // .nt stop <pub name>
        OptionParser parser = newParser();
        stopNonOptionSpec = parser.nonOptions("Publisher name").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".nt stop").unrestricted().originReplies()
            .description("Stop capturing NT stream data").parser(parser).optionAliases(newAliasesMap(parser))
            .command((message, optionSet) -> {
                List<String> publishers = optionSet.valuesOf(stopNonOptionSpec);

                StringBuilder builder = new StringBuilder();
                for (String publisher : publishers) {
                    Optional<NuclearStream> stream = nuclearService.findStreamByPublisher(publisher);
                    if (stream.isPresent()) {
                        if (nuclearService.isStreamEnabled(stream.get())) {
                            nuclearService.stop(stream.get());
                            builder.append("Stopping capture of events from **").append(publisher).append("**\n");
                        } else {
                            builder.append("Not capturing events from **").append(publisher).append("**\n");
                        }
                    }
                }
                return builder.toString();
            }).build());
    }

    private void configureStartCommand() {
        // .nt start <pub name>
        OptionParser parser = newParser();
        startNonOptionSpec = parser.nonOptions("Publisher name").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".nt start").unrestricted().originReplies()
            .description("Start capturing NT stream data").parser(parser).optionAliases(newAliasesMap(parser))
            .command((message, optionSet) -> {
                List<String> publishers = optionSet.valuesOf(startNonOptionSpec);

                StringBuilder builder = new StringBuilder();
                for (String publisher : publishers) {
                    Optional<NuclearStream> stream = nuclearService.findStreamByPublisher(publisher);
                    if (stream.isPresent()) {
                        if (!nuclearService.isStreamEnabled(stream.get())) {
                            nuclearService.start(stream.get());
                            builder.append("Starting capture of events from **").append(publisher).append("**\n");
                        } else {
                            builder.append("Already capturing events from **").append(publisher).append("**\n");
                        }
                    }
                }
                return builder.toString();
            }).build());
    }

    private void configureCreateCommand() {
        // .nt create stream <stream> id <id> name <pub_name>
        OptionParser parser = newParser();
        createStreamSpec = parser.accepts("stream", "NT Stream Key").withRequiredArg().required();
        createIdSpec = parser.accepts("id", "SteamId64").withRequiredArg().required();
        createNameSpec = parser.accepts("name", "Name of the publisher to assign").withRequiredArg().required();
        commandService.register(CommandBuilder.startsWith(".nt create").unrestricted().originReplies()
        .description("Create a NT data stream").parser(parser).optionAliases(newAliasesMap(parser))
        .command((message, optionSet) -> {
            String key = optionSet.valueOf(createStreamSpec);
            String id64 = optionSet.valueOf(createIdSpec);
            String name = optionSet.valueOf(createNameSpec);

            // if the name is not unique the values will be updated
            Publisher publisher = nuclearService.getOrCreatePublisher(name);
            NuclearStream nuclearStream = nuclearService.getOrCreateNuclearStream(id64, key, publisher);
            nuclearStream.setKey(key);
            nuclearStream.setPublisher(publisher);
            nuclearService.save(nuclearStream);
            return ":ok_hand:";
        }).build());
    }
}
