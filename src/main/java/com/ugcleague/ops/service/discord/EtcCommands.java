package com.ugcleague.ops.service.discord;

import com.google.code.chatterbotapi.ChatterBot;
import com.google.code.chatterbotapi.ChatterBotFactory;
import com.google.code.chatterbotapi.ChatterBotSession;
import com.google.code.chatterbotapi.ChatterBotType;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import com.vdurmont.emoji.EmojiParser;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;
import org.springframework.xml.xpath.XPathOperations;
import org.w3c.dom.Element;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.handle.impl.events.MentionEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;

import javax.annotation.PostConstruct;
import javax.xml.transform.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;

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
    private static final Pattern UNICODE = Pattern.compile("\\|([\\da-fA-F]+)");
    private static final Pattern DICE_ROLL = Pattern.compile("((\\d*)?d(\\d+)([+-/*]\\d+)?)");

    private final CommandService commandService;
    private final DiscordService discordService;
    private final RestOperations restTemplate;
    private final XPathOperations xPathTemplate;
    private final Executor taskExecutor;
    private final SettingsService settingsService;
    private final Map<String, ChatterBotSession> chatterBotSessionMap = new ConcurrentHashMap<>();
    private volatile String currentSession;

    private OptionSpec<String> chatterNonOptionSpec;

    @Autowired
    public EtcCommands(CommandService commandService, DiscordService discordService,
                       RestOperations restTemplate, XPathOperations xPathTemplate, Executor taskExecutor,
                       SettingsService settingsService) {
        this.commandService = commandService;
        this.discordService = discordService;
        this.restTemplate = restTemplate;
        this.xPathTemplate = xPathTemplate;
        this.taskExecutor = taskExecutor;
        this.settingsService = settingsService;
    }

    @PostConstruct
    private void configure() {
        initCatApiCommand();
        initChatterBotSessions();
        initChatterCommand();
        initRollCommand();
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

    private void initRollCommand() {
        commandService.register(CommandBuilder.startsWith(".roll")
            .description("Roll dice").unrestricted().originReplies().noParser()
            .command((message, optionSet) -> {
                discordService.deleteMessage(message, 2, TimeUnit.SECONDS);
                if (message.getContent().length() > ".roll".length()) {
                    String arg = message.getContent().split(" ", 2)[1];
                    Matcher matcher = DICE_ROLL.matcher(arg);
                    if (matcher.find()) {
                        try {
                            int rolls = Math.min(100, Integer.parseInt(matcher.group(2)));
                            int sides = Math.min(1000, Integer.parseInt(matcher.group(3)));
                            Function<Integer, Integer> operation = Function.identity();
                            String modifier = matcher.group(4);
                            int m = Integer.parseInt(modifier.substring(1));
                            switch (modifier.charAt(0)) {
                                case '+':
                                case '-':
                                    operation = a -> a + m;
                                    break;
                                case '/':
                                    operation = a -> a / m;
                                    break;
                                case '*':
                                    operation = a -> a * m;
                                    break;
                            }
                            List<Integer> results = new ArrayList<>();
                            for (int i = 0; i < rolls; i++) {
                                results.add(operation.apply(RandomUtils.nextInt(1, sides + 1)));
                            }
                            int total = results.stream().reduce(0, Integer::sum);
                            settingsService.getSettings().getRolls()
                                .computeIfAbsent(message.getAuthor().getID(), k -> new ArrayList<>())
                                .add(new SettingsService.RollData(arg, total));
                            return message.getAuthor().getName() + "#" +
                                message.getAuthor().getDiscriminator() + " rolled " +
                                rolls + 'd' + sides + modifier +
                                " = **" + total + "** " +
                                (results.size() > 1 ? results.toString() : "");
                        } catch (NumberFormatException e) {
                            log.info("Invalid value", e);
                            return "why you do dis " + message.getAuthor().getName() + "?";
                        }
                    } else {
                        return "Invalid format: must be `AdX` with `A` number of dice and `X` sides";
                    }
                } else {
                    int roll = RandomUtils.nextInt(1, 101);
                    settingsService.getSettings().getRolls()
                        .computeIfAbsent(message.getAuthor().getID(), k -> new ArrayList<>())
                        .add(new SettingsService.RollData("d100", roll));
                    return message.getAuthor().getName() + "#" +
                        message.getAuthor().getDiscriminator() + " rolled a **" + roll + "**";
                }
            }).build());
    }

    @EventSubscriber
    public void onMention(MentionEvent event) {
        IMessage message = event.getMessage();
        IChannel channel = event.getMessage().getChannel();
        IUser author = event.getMessage().getAuthor();
        boolean everyone = message.mentionsEveryone();
        boolean dm = channel.isPrivate();
        boolean self = event.getClient().getOurUser().equals(author);
        boolean bl = settingsService.getSettings().getSoundBitesBlacklist().contains(message.getChannel().getID());
        if (!everyone && !dm && !self && !bl) {
            CompletableFuture.runAsync(() -> {
                // TODO handle the typing status on concurrent requests
                channel.toggleTypingStatus();
                long start = System.currentTimeMillis();
                String content = EmojiParser.parseToAliases(message.getContent()
                        .replace(discordService.getClient().getOurUser().mention(), ""),
                    EmojiParser.FitzpatrickAction.REMOVE);
                try {
                    String response = chatterBotSessionMap.get(currentSession).think(content);
                    response = StringEscapeUtils.unescapeHtml4(response);
                    Matcher matcher = UNICODE.matcher(response);
                    while (matcher.find()) {
                        String hex = matcher.group(1);
                        response = matcher.replaceFirst(new String(Character.toChars(Integer.parseInt(hex, 16))));
                    }
                    long delay = System.currentTimeMillis() - start;
                    log.debug("Response took {} ms", delay);
                    if (delay < 3000L) {
                        Thread.sleep(3000L - delay);
                    }
                    discordService.sendMessage(channel, author.mention() + " " + response);
                } catch (Exception e) {
                    log.warn("Could not process chatter input", e);
                    channel.toggleTypingStatus();
                }
            }, taskExecutor);
        }
    }
}
