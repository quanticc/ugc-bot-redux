package com.ugcleague.ops.service.discord;

import com.google.code.chatterbotapi.ChatterBot;
import com.google.code.chatterbotapi.ChatterBotFactory;
import com.google.code.chatterbotapi.ChatterBotSession;
import com.google.code.chatterbotapi.ChatterBotType;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
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
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.handle.impl.events.MentionEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;

import javax.annotation.PostConstruct;
import javax.xml.transform.Source;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

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
public class EtcCommands implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(EtcCommands.class);

    private final CommandService commandService;
    private final DiscordService discordService;
    private final RestOperations restTemplate;
    private final XPathOperations xPathTemplate;
    private final Executor taskExecutor;
    private final Random random = new Random();
    private final Map<String, ChatterBotSession> chatterBotSessionMap = new ConcurrentHashMap<>();
    private volatile String currentSession;

    private Command rateCommand;
    private OptionSpec<Integer> rateNumberSpec;
    private OptionSpec<Integer> rateWaitSpec;
    private OptionSpec<Boolean> rateStatusSpec;
    private OptionSpec<String> rateNonOptionSpec;
    private OptionSpec<String> chatterNonOptionSpec;

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
        initChatterBotSessions();
        initChatterCommand();
        discordService.subscribe(this);
    }

    private void initChatterBotSessions() {
        ChatterBotFactory factory = new ChatterBotFactory();
        try {
            ChatterBot pandora = factory.create(ChatterBotType.PANDORABOTS, "b0dafd24ee35a477");
            ChatterBotSession pandoraSession = pandora.createSession();
            chatterBotSessionMap.put("Pandora", pandoraSession);
        } catch (Exception e) {
            log.warn("Could not create PandoraBots session", e);
        }
        try {
            ChatterBot clever = factory.create(ChatterBotType.CLEVERBOT);
            ChatterBotSession cleverSession = clever.createSession();
            chatterBotSessionMap.put("CleverBot", cleverSession);
            currentSession = "CleverBot";
        } catch (Exception e) {
            log.warn("Could not create CleverBot session", e);
        }
//        try {
//            ChatterBot jabber = factory.create(ChatterBotType.JABBERWACKY);
//            ChatterBotSession jabberSession = jabber.createSession();
//            chatterBotSessionMap.put("jabber", jabberSession);
//        } catch (Exception e) {
//            log.warn("Could not create Jabberwacky session", e);
//        }
    }

    private void initChatterCommand() {
        OptionParser parser = newParser();
        String keys = chatterBotSessionMap.keySet().stream().collect(Collectors.joining(", "));
        chatterNonOptionSpec = parser.nonOptions("Engine name, one of: " + keys).ofType(String.class);
        commandService.register(CommandBuilder.anyMatch(".chatter")
            .description("Configures chatter engine").support().originReplies()
            .parser(parser).command(this::chatter).build());
    }

    private String chatter(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(chatterNonOptionSpec);
        if (optionSet.has("?") || nonOptions.isEmpty()) {
            return null;
        }
        String engine = nonOptions.get(0);
        ChatterBotSession value = chatterBotSessionMap.get(engine);
        if (value == null) {
            String keys = chatterBotSessionMap.keySet().stream().collect(Collectors.joining(", "));
            return "Invalid chatter engine, must be one of: " + keys;
        }
        currentSession = engine;
        return "Chatter engine switched";
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

    @EventSubscriber
    public void onMention(MentionEvent event) {
        IMessage message = event.getMessage();
        boolean everyone = message.mentionsEveryone();
        if (!everyone) {
            CompletableFuture.runAsync(() -> {
                String content = message.getContent()
                    .replace(discordService.getClient().getOurUser().mention(), "");
                try {
                    message.reply(chatterBotSessionMap.get(currentSession).think(content));
                } catch (Exception e) {
                    log.warn("Could not process chatter input", e);
                }
            }, taskExecutor);
        }
    }
}
