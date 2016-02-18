package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordCacheService;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static org.apache.commons.lang3.StringUtils.leftPad;

/**
 * Commands to retrieve info and general operation over Discord users
 * <ul>
 *     <li>userinfo</li>
 * </ul>
 */
@Service
public class UserPresenter {

    private static final int TIMESTAMP_BITSHIFT = 22;
    private static final long DISCORD_EPOCH = 1420070400000L;

    private final CommandService commandService;
    private final DiscordCacheService cacheService;
    private final DiscordService discordService;

    private OptionSpec<String> whoNonOptionSpec;

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
    }

    private String executeWho(IMessage message, OptionSet optionSet) {
        Set<String> keys = optionSet.valuesOf(whoNonOptionSpec).stream().collect(Collectors.toSet());
        if (optionSet.has("?") || keys.isEmpty()) {
            return null;
        }
        IGuild guild = (message.getChannel().isPrivate() ? null : message.getChannel().getGuild());
        List<IUser> users = discordService.getClient().getGuilds().stream().flatMap(g -> g.getUsers().stream()).collect(Collectors.toList());
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
                    .append((user.getGame().isPresent() ? leftPad("Playing: ", pad) + user.getGame().get() + "\n" : ""))
                    .append(leftPad("Roles: ", pad)).append(guild != null ? formatList(user.getRolesForGuild(guild.getID())) : "<not shown>")
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
