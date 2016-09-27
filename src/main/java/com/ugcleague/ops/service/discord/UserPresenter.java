package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordCacheService;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.MessageList;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.util.DateUtil.formatRelative;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.leftPad;

/**
 * Commands to retrieve info and general operation over Discord users
 * <ul>
 * <li>userinfo</li>
 * <li>mentions</li>
 * </ul>
 */
@Service
@Transactional
public class UserPresenter {

    private static final Logger log = LoggerFactory.getLogger(UserPresenter.class);

    private static final int TIMESTAMP_BITSHIFT = 22;
    private static final long DISCORD_EPOCH = 1420070400000L;
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@([0-9]+)>");

    private final CommandService commandService;
    private final DiscordCacheService cacheService;
    private final DiscordService discordService;

    private OptionSpec<String> whoNonOptionSpec;
    private OptionSpec<String> mentionsNonOptionSpec;
    private OptionSpec<String> mentionsUserSpec;
    private OptionSpec<Boolean> mentionsDirectSpec;

    @Autowired
    public UserPresenter(CommandService commandService, DiscordCacheService cacheService, DiscordService discordService) {
        this.commandService = commandService;
        this.cacheService = cacheService;
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = newParser();
        whoNonOptionSpec = parser.nonOptions("User id, names or mentions of the users to retrieve information").ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".userinfo").description("Gets info about a Discord user")
            .unrestricted().originReplies().queued().parser(parser).command(this::executeWho).build());
        initMentionsCommand();
    }

    private void initMentionsCommand() {
        OptionParser parser = newParser();
        mentionsNonOptionSpec = parser.nonOptions("Number of mentions to show").ofType(String.class);
        mentionsUserSpec = parser.acceptsAll(asList("o", "u", "of", "user"), "Search this user mentions (instead of yourself)")
            .withRequiredArg();
        mentionsDirectSpec = parser.acceptsAll(asList("d", "direct"), "Exclude at-everyone mentions")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        Map<String, String> aliases = newAliasesMap();
        aliases.put("of", "--user");
        aliases.put("user", "--user");
        aliases.put("direct", "--direct");
        commandService.register(CommandBuilder.anyMatch(".mentions").description("Check for your latest mentions")
            .unrestricted().privateReplies().parser(parser).optionAliases(aliases).queued()
            .command(this::executeMentioned).build());
    }

    private String executeMentioned(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(mentionsNonOptionSpec);
        int maxMessagesToGet = 500;
        int maxMentionsToFind = 1;
        int mentionsFound = 0;
        if (!nonOptions.isEmpty()) {
            String arg = nonOptions.get(0);
            if (arg.matches("[0-9]+")) {
                try {
                    maxMentionsToFind = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    log.warn("Entered invalid # of mentions to search: {}", arg);
                }
            } else if (arg.equalsIgnoreCase("all")) {
                maxMentionsToFind = 0;
            }
        }

        IUser user = message.getAuthor();

        if (optionSet.has(mentionsUserSpec)) {
            String key = optionSet.valueOf(mentionsUserSpec);
            List<IUser> users = discordService.getClient().getGuilds().stream()
                .map(IGuild::getUsers).flatMap(List::stream).collect(Collectors.toList());
            String id = key.replaceAll("<@([0-9]+)>", "$1");
            List<IUser> matching = users.stream()
                .filter(u -> u.getID().equals(id) || u.getName().equalsIgnoreCase(key))
                .distinct().collect(Collectors.toList());
            int limit = 5;
            if (matching.size() == 1) {
                user = matching.get(0);
            } else if (matching.size() > 1) {
                int remaining = matching.size() - limit;
                return "Multiple user matches for " + key + ": " + matching.stream()
                    .limit(limit).map(u -> u.getName() + " (id: " + u.getID() + ")").collect(Collectors.joining(", "))
                    + (remaining > 0 ? " and " + remaining + " more...\n" : "\n");
            } else {
                return "Could not find an user with that name or ID";
            }
        }

        boolean excludeEveryoneMentions = optionSet.has(mentionsDirectSpec) && optionSet.valueOf(mentionsDirectSpec);
        StringBuilder response = new StringBuilder();
        MessageList messageList = message.getChannel().getMessages();
        long lastMessage = System.currentTimeMillis();
        for (int i = 0; i < maxMessagesToGet && (maxMentionsToFind == 0 || mentionsFound < maxMentionsToFind); i++) {
            if (Thread.interrupted()) {
                log.warn("Execution was interrupted");
                break;
            }
            long now = System.currentTimeMillis();
            if (i % 50 == 0 || now - lastMessage > 5000L) {
                log.debug("Retrieving message #{}", i);
                lastMessage = now;
            }
            if (i >= messageList.size()) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    log.debug("Interrupted while scanning channel history after {} messages", i);
                    break;
                }
            }
            // TODO: handle 429s by using load() instead of get()
            try {
                IMessage msg = messageList.get(i);
                if (msg.getMentions().contains(user) || (!excludeEveryoneMentions && msg.mentionsEveryone())) {
                    mentionsFound++;
                    response.append("• Mentioned by ").append(msg.getAuthor().mention())
                        .append(", ").append(formatRelative(msg.getTimestamp()))
                        .append(": ").append(limit(resolveMentions(msg.getContent()), 100)).append("\n");
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                log.debug("Reached the beginning of this channel after {} messages", i);
                break;
            }
        }

        if (mentionsFound == 0) {
            return "No mentions in the last " + maxMessagesToGet + " messages";
        }

        return response.toString();
    }

    private String limit(String input, int maxLength) {
        String ellipsis = "...";
        int maxLengthWithEllipse = maxLength - ellipsis.length();
        if (input.length() > maxLengthWithEllipse) {
            return input.substring(0, maxLengthWithEllipse) + ellipsis;
        }
        return input;
    }

    private String resolveMentions(String content) {
        // "escapes" a mention and translates ID to username if possible
        Matcher matcher = USER_MENTION_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group(1);
            IUser user = discordService.getClient().getUserByID(id);
            if (user == null) {
                matcher.appendReplacement(buffer, "@" + id);
            } else {
                matcher.appendReplacement(buffer, "@" + user.getName());
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String executeWho(IMessage message, OptionSet optionSet) {
        Set<String> keys = optionSet.valuesOf(whoNonOptionSpec).stream().collect(Collectors.toSet());
        if (optionSet.has("?") || keys.isEmpty()) {
            return null;
        }
        IGuild guild = (message.getChannel().isPrivate() ? null : message.getChannel().getGuild());
        List<IUser> users = discordService.getClient().getGuilds().stream()
            .map(IGuild::getUsers).flatMap(List::stream).collect(Collectors.toList());
        StringBuilder builder = new StringBuilder();
        int limit = 5;
        for (String key : keys) {
            String id = key.replaceAll("<@([0-9]+)>", "$1");
            List<IUser> matching = users.stream()
                .filter(u -> u.getID().equals(id) || u.getName().equalsIgnoreCase(key))
                .distinct().collect(Collectors.toList());
            if (matching.size() == 1) {
                IUser user = matching.get(0);
                cacheService.getOrCreateUser(user); // refresh cached value
                int pad = 10;
                builder.append("```\n").append(leftPad("Username: ", pad)).append(user.getName()).append("#").append(user.getDiscriminator()).append("\n")
                    .append(leftPad("ID: ", pad)).append("<").append(user.getID()).append(">\n")
                    .append(leftPad("Joined: ", pad)).append(discordIdToUtc(user.getID())).append("\n")
                    .append(leftPad("Status: ", pad)).append(user.getPresence().name().toLowerCase()).append("\n")
                    .append(leftPad("Roles: ", pad)).append(guild != null ? formatList(user.getRolesForGuild(guild)) : "<not shown>")
                    .append("\n```").append(user.getAvatarURL()).append("\n");
            } else if (matching.size() > 1) {
                int remaining = matching.size() - limit;
                builder.append("Multiple matches for ").append(key).append(": ").append(matching.stream()
                    .limit(limit).map(u -> u.getName() + " (id: " + u.getID() + ")").collect(Collectors.joining(", ")))
                    .append(remaining > 0 ? " and " + remaining + " more...\n" : "\n");
            } else if (keys.size() == 1) {
                builder.append("Could not find an user with that name or ID");
            }
        }
        return builder.toString();
    }

    private String formatList(List<IRole> roles) {
        String names = roles.stream().map(IRole::getName).filter(s -> !s.equals("@everyone")).collect(Collectors.joining(", "));
        if (names.isEmpty()) {
            return "<none>";
        } else {
            return names;
        }
    }

    private Instant discordIdToUtc(String id) {
        return Instant.ofEpochMilli((Long.parseLong(id) >> TIMESTAMP_BITSHIFT) + DISCORD_EPOCH);
    }
}
