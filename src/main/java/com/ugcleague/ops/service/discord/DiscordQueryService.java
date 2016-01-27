package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;

import static com.ugcleague.ops.util.DateUtil.formatHuman;

@Service
public class DiscordQueryService {

    private static final Logger log = LoggerFactory.getLogger(DiscordQueryService.class);

    private final CommandService commandService;
    private final DiscordService discordService;

    @Autowired
    public DiscordQueryService(CommandService commandService, DiscordService discordService) {
        this.commandService = commandService;
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        initDiscordInfoCommand();
    }

    private void initDiscordInfoCommand() {
        commandService.register(CommandBuilder.equalsTo(".beep info")
            .description("Get Discord information about the bot").permission(0).command(this::executeInfoCommand).build());
    }

    private String executeInfoCommand(IMessage m, OptionSet o) {
        StringBuilder builder = new StringBuilder();
        IUser master = discordService.getMasterUser();
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptime = rb.getUptime();
        builder.append("Hello! I'm here to help with UGC support.\n")
            .append(String.format("I was built by %s using the Discord4J library v%s.\n" +
                "I'm currently running v%s for %s.", master.mention(), "2.1.3", "0.2.0", formatHuman(Duration.ofMillis(uptime))));
        return builder.toString();
    }
}
