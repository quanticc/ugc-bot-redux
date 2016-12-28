package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordUtil;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.MessageList;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.service.discord.util.DiscordUtil.deleteInBatch;
import static java.util.Arrays.asList;

@Service
public class ModerationPresenter {

    private static final Logger log = LoggerFactory.getLogger(ModerationPresenter.class);

    private final DiscordService discordService;
    private final CommandService commandService;

    private OptionSpec<Integer> deleteLastSpec;
    private OptionSpec<String> deleteMatchingSpec;
    private OptionSpec<String> deleteLikeSpec;
    private OptionSpec<String> deleteFromSpec;
    private OptionSpec<String> deleteBeforeSpec;
    private OptionSpec<String> deleteAfterSpec;

    @Autowired
    public ModerationPresenter(DiscordService discordService, CommandService commandService) {
        this.discordService = discordService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = newParser();
        Map<String, String> aliases = newAliasesMap();
        aliases.put("last", "--last");
        aliases.put("matching", "--matching");
        aliases.put("like", "--like");
        aliases.put("from", "--from");
        aliases.put("before", "--before");
        aliases.put("until", "--until");
        aliases.put("after", "--after");
        aliases.put("since", "--since");
        deleteLastSpec = parser.accepts("last", "Limit deletion to the latest N").withRequiredArg()
            .ofType(Integer.class).describedAs("N").defaultsTo(100);
        deleteMatchingSpec = parser.accepts("matching", "Delete messages matching this regex").withRequiredArg()
            .describedAs("regex");
        deleteLikeSpec = parser.accepts("like", "Delete messages containing this string").withRequiredArg()
            .describedAs("string");
        deleteFromSpec = parser.accepts("from", "Delete messages from this user (@mention, name or ID)").withRequiredArg()
            .describedAs("user");
        deleteBeforeSpec = parser.acceptsAll(asList("before", "until"), "Delete messages before this time").withRequiredArg()
            .describedAs("timex");
        deleteAfterSpec = parser.acceptsAll(asList("after", "since"), "Delete messages after this time").withRequiredArg()
            .describedAs("timex");
        commandService.register(CommandBuilder.startsWith(".delete").support()
            .description("Delete messages in this channel").parser(parser).optionAliases(aliases)
            .privateReplies().command(this::delete).build());
    }

    private String delete(IMessage message, OptionSet optionSet) {
        IChannel channel = message.getChannel();
        if (channel.isPrivate()) {
            return "This command does not work for private messages, use `.unsay`";
        }
        if (!optionSet.has(deleteLastSpec)
            && !optionSet.has(deleteMatchingSpec)
            && !optionSet.has(deleteLikeSpec)
            && !optionSet.has(deleteFromSpec)
            && !optionSet.has(deleteBeforeSpec)
            && !optionSet.has(deleteAfterSpec)) {
            // require at least 1 explicit criteria
            return "Please specify at least one deletion criteria: last, matching, like, from, before, after";
        }
        MessageList messages = channel.getMessages();
        int capacity = messages.getCacheCapacity();
        messages.setCacheCapacity(MessageList.UNLIMITED_CAPACITY);
        ZonedDateTime before = null;
        ZonedDateTime after = null;
        if (optionSet.has(deleteBeforeSpec)) {
            before = parseTimeDate(optionSet.valueOf(deleteBeforeSpec));
        }
        if (optionSet.has(deleteAfterSpec)) {
            after = parseTimeDate(optionSet.valueOf(deleteAfterSpec));
        }
        IUser authorToMatch = null;
        if (optionSet.has(deleteFromSpec)) {
            String key = optionSet.valueOf(deleteFromSpec).replaceAll("<@!?([0-9]+)>", "$1");
            List<IUser> matching = discordService.getClient().getGuilds().stream()
                .flatMap(g -> g.getUsers().stream())
                .filter(u -> u.getName().equalsIgnoreCase(key) || u.getID().equals(key) || key.equals(u.getName() + u.getDiscriminator()))
                .distinct().collect(Collectors.toList());
            if (matching.size() > 1) {
                StringBuilder builder = new StringBuilder("Multiple users matched, please narrow search or use ID\n");
                for (IUser user : matching) {
                    builder.append(user.getName()).append(" has id `").append(user.getID()).append("`\n");
                }
                return builder.toString();
            } else if (matching.isEmpty()) {
                return "User " + key + " not found in cache";
            } else {
                authorToMatch = matching.get(0);
            }
        }
        // collect all offending messages
        List<IMessage> toDelete = new ArrayList<>();
        int i = 0;
        int max = !optionSet.has(deleteLastSpec) ? 100 : Math.max(1, optionSet.valueOf(deleteLastSpec));
        log.debug("Searching for up to {} messages from {}", max, DiscordUtil.toString(channel));
        while (i < max) {
            try {
                IMessage msg = messages.get(i++);
                // continue if we are after "--before" timex
                if (before != null && msg.getTimestamp().isAfter(before.toLocalDateTime())) {
                    continue;
                }
                // break if we reach "--after" timex
                if (after != null && msg.getTimestamp().isBefore(after.toLocalDateTime())) {
                    log.debug("Search interrupted after hitting time constraint");
                    break;
                }
                // exclude by content (.matches)
                if (optionSet.has(deleteMatchingSpec) &&
                    !msg.getContent().matches(optionSet.valueOf(deleteMatchingSpec))) {
                    continue;
                }
                // exclude by content (.contains)
                if (optionSet.has(deleteLikeSpec) &&
                    !msg.getContent().contains(optionSet.valueOf(deleteLikeSpec))) {
                    continue;
                }
                // exclude by author
                if (authorToMatch != null && !msg.getAuthor().equals(authorToMatch)) {
                    continue;
                }
                toDelete.add(msg);
            } catch (ArrayIndexOutOfBoundsException e) {
                // we reached the end apparently
                log.warn("Could not retrieve messages to delete", e);
                break;
            }
        }
        deleteInBatch(channel, toDelete);
        messages.setCacheCapacity(capacity);
        return (toDelete.size() == 0 ? "No messages were deleted" : "Deleted " + toDelete.size() + " message" + (toDelete.size() == 1 ? "" : "s"));
    }

    private ZonedDateTime parseTimeDate(String s) {
        List<Date> parsed = new PrettyTimeParser().parse(s); // never null, can be empty
        if (!parsed.isEmpty()) {
            Date first = parsed.get(0);
            return ZonedDateTime.ofInstant(first.toInstant(), ZoneId.systemDefault());
        }
        log.warn("Could not parse a valid date from input: {}", s);
        return null;
    }
}
