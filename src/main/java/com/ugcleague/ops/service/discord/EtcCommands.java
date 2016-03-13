package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.StatusWrapper;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;
import org.springframework.xml.xpath.XPathOperations;
import org.w3c.dom.Element;
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import javax.xml.transform.Source;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;

/**
 * Miscellaneous commands that do not fit in another category.
 * <ul>
 * <li>cat</li>
 * <li>echo</li>
 * </ul>
 */
@Service
public class EtcCommands {

    private static final Logger log = LoggerFactory.getLogger(EtcCommands.class);

    private final CommandService commandService;
    private final DiscordService discordService;
    private final RestOperations restTemplate;
    private final XPathOperations xPathTemplate;
    private final Executor taskExecutor;
    private final Random random = new Random();

    private Command rateCommand;
    private OptionSpec<Integer> rateNumberSpec;
    private OptionSpec<Integer> rateWaitSpec;
    private OptionSpec<Boolean> rateStatusSpec;
    private OptionSpec<String> rateNonOptionSpec;

    @Autowired
    public EtcCommands(CommandService commandService, DiscordService discordService,
                       RestOperations restTemplate, XPathOperations xPathTemplate, Executor taskExecutor) {
        this.commandService = commandService;
        this.discordService = discordService;
        this.restTemplate = restTemplate;
        this.xPathTemplate = xPathTemplate;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    private void configure() {
        initCatApiCommand();
        initRateTestCommand();
    }

    private void initCatApiCommand() {
        commandService.register(CommandBuilder.equalsTo(".cat")
            .description("(=ↀωↀ=)✧").support().originReplies().command((message, optionSet) -> {
                String url = "http://thecatapi.com/api/images/get?format=xml";
                CompletableFuture.supplyAsync(() -> {
                    Source source = restTemplate.getForObject(url, Source.class);
                    List<String> images = xPathTemplate.evaluate("//image", source, (node, i) -> {
                        Element image = (Element) node;
                        return image.getElementsByTagName("url").item(0).getTextContent();
                    });
                    if (images != null && !images.isEmpty()) {
                        return images.get(0);
                    } else {
                        return "";
                    }
                }, taskExecutor).thenAccept(s -> {
                    if (!s.isEmpty()) {
                        try {
                            discordService.sendMessage(message.getChannel(), s);
                        } catch (DiscordException | MissingPermissionsException | InterruptedException e) {
                            log.warn("Could not send cat response: {}", e.toString());
                        }
                    }
                });
                return "";
            }).build());
    }

    private void initRateTestCommand() {
        OptionParser parser = newParser();
        rateNumberSpec = parser.acceptsAll(asList("n", "number"), "Number of messages to send")
            .withRequiredArg().ofType(Integer.class);
        rateWaitSpec = parser.acceptsAll(asList("w", "wait"), "Milliseconds before each message")
            .withRequiredArg().ofType(Integer.class).defaultsTo(1000);
        rateStatusSpec = parser.acceptsAll(asList("s", "status"), "Display progress with successive edits to the initial reply")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        rateNonOptionSpec = parser.nonOptions("Messages to be displayed").ofType(String.class);
        rateCommand = CommandBuilder.anyMatch(".echo")
            .description("Echo a series of messages").support().originReplies().queued().parser(parser)
            .command(this::echo).build();
        commandService.register(rateCommand);
    }

    private String echo(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }
        List<String> messages = optionSet.valuesOf(rateNonOptionSpec);
        int limit = optionSet.has(rateNumberSpec) ? Math.max(1, optionSet.valueOf(rateNumberSpec)) : (messages.isEmpty() ? 10 : messages.size());
        long sleep = Math.max(1, optionSet.valueOf(rateWaitSpec));
        boolean status = optionSet.has(rateStatusSpec) ? optionSet.valueOf(rateStatusSpec) : false;
        String[] table = {"(╯°□°）╯︵ ┻━┻", "┬─┬\uFEFF ノ( ゜-゜ノ)"};
        String[] dancing = {"╚(ಠ_ಠ)╗ ♪ ♫ ♪", "╔(ಠ_ಠ)╝ ♫ ♪ ♫"};
        String[] states = random.nextBoolean() ? table : dancing;
        for (int i = 0; i < limit; i++) {
            try {
                if (status) {
                    String content = messages.isEmpty() ? states[i % states.length] : messages.get(i % messages.size());
                    commandService.statusReplyFrom(message, rateCommand,
                        StatusWrapper.ofWork(i + 1, limit)
                            .withMessage(content).bar().text());
                } else {
                    String content = messages.isEmpty() ? states[i % states.length] : messages.get(i % messages.size());
                    commandService.replyFrom(message, rateCommand, content);
                }
                Thread.sleep(sleep);
            } catch (DiscordException | MissingPermissionsException | InterruptedException e) {
                log.warn("Could not send test message", e);
            }
        }
        return "";
    }
}
