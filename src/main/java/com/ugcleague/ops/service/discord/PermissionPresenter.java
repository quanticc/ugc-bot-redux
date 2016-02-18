package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.*;
import com.ugcleague.ops.domain.util.PermissionProvider;
import com.ugcleague.ops.service.DiscordCacheService;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.PermissionService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.obj.*;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;

/**
 * Commands to perform operations about command permissions.
 * <ul>
 * <li>perm</li>
 * </ul>
 */
@Service
@Transactional
public class PermissionPresenter {

    private static final Logger log = LoggerFactory.getLogger(PermissionPresenter.class);

    private final PermissionService permissionService;
    private final DiscordService discordService;
    private final DiscordCacheService cacheService;
    private final CommandService commandService;

    private OptionSpec<String> permNonOptionSpec;

    @Autowired
    public PermissionPresenter(PermissionService permissionService, DiscordService discordService,
                               DiscordCacheService cacheService, CommandService commandService) {
        this.permissionService = permissionService;
        this.discordService = discordService;
        this.cacheService = cacheService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        OptionParser parser = newParser();
        String nonOptDesc = "Must conform with the spec: `<permission> <action> <type> <key>` where:\n" +
            "`<permission>` is the permission's name\n" + "`<action>` is one of: allow, deny, reset, list\n" +
            "\tuse **allow** to give permissions, **deny** to block them, **reset** to remove them and **list** to display current config\n" +
            "`<type>` is one of: server, role, channel, user, this\n" +
            "`<key>` depends on the type:\n\tif using guild, role, channel or user it's the name or ID of the entity,\n" +
            "\tadditionally when using role, you can add two extra parameters `in <server id or name>` to make it specific " +
            "to a server instead of any server or the current one\n" +
            "\tif using `this`, it's another qualifier like server, channel";
        permNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".perm").description("Manages bot permissions")
            .master().originReplies().mention().parser(parser).command(this::managePermissions).build());
    }

    private String managePermissions(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(permNonOptionSpec);
        if (optionSet.has("?") || nonOptions.size() < 2) {
            return null;
        }
        String permName = nonOptions.get(0).toLowerCase();
        String action = nonOptions.get(1).toLowerCase();
        Optional<Permission> permission = permissionService.findPermissionByName(permName);
        List<String> args = nonOptions.subList(2, nonOptions.size());
        if (action.equals("list")) {
            return listPermissions(message, permName, args);
        } else if (action.equals("evict")) {
            permissionService.evict();
            return "OK";
        } else if (!permission.isPresent()) {
            return "Not a valid permission name";
        } else if (action.equals("allow")) {
            return editPermission(Operation.ALLOW, message, permission.get(), args);
        } else if (action.equals("deny")) {
            return editPermission(Operation.DENY, message, permission.get(), args);
        } else if (action.equals("reset")) {
            return editPermission(Operation.RESET, message, permission.get(), args);
        }
        return "Invalid operation! Check details with `.perm -?`";
    }

    private String listPermissions(IMessage message, String permName, List<String> args) {
        return permissionService.findAllPermissions().stream()
            .filter(p -> p.getName().contains(permName))
            .map(p -> listPermissions(message, p, args))
            .collect(Collectors.joining("\n"));
    }

    private String listPermissions(IMessage message, Permission permission, List<String> args) {
        List<String> matches = new ArrayList<>();
        // TODO narrow search by following args
        for (DiscordGuild guild : cacheService.findAllGuilds()) {
            if (guild.getDenied().contains(permission)) {
                matches.add(String.format("deny everyone in guild %s (%s)", nullAsEmpty(guild.getName()), guild.getId()));
            } else if (guild.getAllowed().contains(permission)) {
                matches.add(String.format("allow anyone in guild %s (%s)", nullAsEmpty(guild.getName()), guild.getId()));
            }
            for (DiscordRole role : guild.getRoles()) {
                if (role.getDenied().contains(permission)) {
                    matches.add(String.format("deny everyone with role %s (%s) in guild %s (%s)",
                        nullAsEmpty(role.getName()), role.getId(), nullAsEmpty(guild.getName()), guild.getId()));
                } else if (role.getAllowed().contains(permission)) {
                    matches.add(String.format("allow anyone with role %s (%s) in guild %s (%s)",
                        nullAsEmpty(role.getName()), role.getId(), nullAsEmpty(guild.getName()), guild.getId()));
                }
            }
        }
        for (DiscordChannel channel : cacheService.findAllChannels()) {
            if (channel.getDenied().contains(permission)) {
                matches.add(String.format("deny everyone in channel %s (%s)", nullAsEmpty(channel.getName()), channel.getId()));
            } else if (channel.getAllowed().contains(permission)) {
                matches.add(String.format("allow anyone in channel %s (%s)", nullAsEmpty(channel.getName()), channel.getId()));
            }
        }
        for (DiscordUser user : cacheService.findAllUsers()) {
            if (user.getDenied().contains(permission)) {
                matches.add(String.format("deny user %s (%s)", nullAsEmpty(user.getName()), user.getId()));
            } else if (user.getAllowed().contains(permission)) {
                matches.add(String.format("allow user %s (%s)", nullAsEmpty(user.getName()), user.getId()));
            }
        }
        if (permission.isDefaultAllow()) {
            matches.add("allow if not previously denied");
        }
        return matches.stream().collect(Collectors.joining(", "));
    }

    private String nullAsEmpty(String original) {
        return original == null ? "" : original;
    }

    private String editPermission(Operation op, IMessage message, Permission permission, List<String> args) {
        String first = args.size() >= 1 ? args.get(0) : "";
        String second = args.size() >= 2 ? args.get(1) : "";
        String third = args.size() >= 3 ? args.get(2) : "";
        String fourth = args.size() >= 4 ? args.get(3) : "";
        if (first.equalsIgnoreCase("this")) {
            // this <channel|guild>
            if (second.equalsIgnoreCase("channel")) {
                // this channel
                return editChannelPermission(op, permission, message.getChannel());
            } else if (second.equalsIgnoreCase("server")) {
                // this guild
                if (message.getChannel().isPrivate()) {
                    return "Not a valid call from private channels";
                }
                IGuild guild = message.getChannel().getGuild();
                return editGuildPermission(op, permission, guild);
            } else {
                return "Use `this channel` or `this server`";
            }
        } else if (first.equalsIgnoreCase("user")) {
            // user <name or id>
            List<IUser> matching = discordService.getClient().getGuilds().stream()
                .flatMap(g -> g.getUsers().stream())
                .filter(u -> u.getName().equalsIgnoreCase(second) || u.getID().equals(second))
                .distinct().collect(Collectors.toList());
            if (matching.size() > 1) {
                StringBuilder builder = new StringBuilder("Multiple users matched, please narrow search or use ID\n");
                for (IUser user : matching) {
                    builder.append(user.getName()).append(" has id `").append(user.getID()).append("`\n");
                }
                return builder.toString();
            } else if (matching.isEmpty()) {
                return "User " + second + " not found in cache";
            } else {
                return editUserPermission(op, permission, matching.get(0));
            }
        } else if (first.equalsIgnoreCase("channel")) {
            // channel <name or id>
            List<IChannel> matching = discordService.getClient().getGuilds().stream()
                .flatMap(g -> g.getChannels().stream())
                .filter(c -> c.getName().equalsIgnoreCase(second) || c.getID().equals(second))
                .distinct().collect(Collectors.toList());
            if (matching.size() > 1) {
                StringBuilder builder = new StringBuilder("Multiple channels matched, please narrow search or use ID\n");
                for (IChannel channel : matching) {
                    builder.append(channel.getName()).append(" has id `").append(channel.getID()).append("`\n");
                }
                return builder.toString();
            } else if (matching.isEmpty()) {
                return "Channel " + second + " not found in cache";
            } else {
                return editChannelPermission(op, permission, matching.get(0));
            }
        } else if (first.equalsIgnoreCase("server")) {
            // guild <name or id>
            List<IGuild> matching = discordService.getClient().getGuilds().stream()
                .filter(g -> g.getName().equalsIgnoreCase(second) || g.getID().equals(second))
                .distinct().collect(Collectors.toList());
            if (matching.size() > 1) {
                StringBuilder builder = new StringBuilder("Multiple servers matched, please narrow search or use ID\n");
                for (IGuild guild : matching) {
                    builder.append(guild.getName()).append(" has id `").append(guild.getID()).append("`\n");
                }
                return builder.toString();
            } else if (matching.isEmpty()) {
                return "Server " + second + " not found in cache";
            } else {
                return editGuildPermission(op, permission, matching.get(0));
            }
        } else if (first.equalsIgnoreCase("role")) {
            // role <name or id> [in <guild name or id>]
            // when a guild name/id is not specified:
            //      if triggered from a private channel, all case insensitive matches are picked up
            //      if triggered from a public channel, only the channel's guild is searched
            boolean isPrivate = message.getChannel().isPrivate();
            boolean specific = third.equalsIgnoreCase("in") && !fourth.isEmpty();
            List<IRole> matching = discordService.getClient().getGuilds().stream()
                .filter(g -> {
                    if (isPrivate) {
                        return !specific || g.getName().equalsIgnoreCase(fourth) || g.getID().equals(fourth);
                    } else {
                        if (!specific) {
                            return g.equals(message.getChannel().getGuild());
                        } else {
                            return g.getName().equalsIgnoreCase(fourth) || g.getID().equals(fourth);
                        }
                    }
                })
                .flatMap(g -> g.getRoles().stream())
                .filter(r -> r.getName().equalsIgnoreCase(second) || r.getID().equals(second))
                .distinct().collect(Collectors.toList());
            if (matching.size() > 1) {
                StringBuilder builder = new StringBuilder("Multiple role matched, please narrow search or use ID\n");
                for (IRole role : matching) {
                    builder.append(role.getName()).append(" has id `").append(role.getID()).append("`\n");
                }
                return builder.toString();
            } else if (matching.isEmpty()) {
                return "Role " + second + " not found in cache";
            } else {
                return editRolePermission(op, permission, matching.get(0));
            }
        } else {
            return "Invalid argument format! Check details with `.perm -?`";
        }
    }

    private void changePermission(Operation op, Permission permission, PermissionProvider permissible) {
        if (op == Operation.ALLOW) {
            permissible.getAllowed().add(permission);
            permissible.getDenied().remove(permission);
        } else if (op == Operation.DENY) {
            permissible.getDenied().add(permission);
            permissible.getAllowed().remove(permission);
        } else if (op == Operation.RESET) {
            permissible.getAllowed().remove(permission);
            permissible.getDenied().remove(permission);
        }
    }

    private String editUserPermission(Operation op, Permission permission, IUser user) {
        DiscordUser u = cacheService.findUserById(user.getID()).orElseGet(() -> new DiscordUser(user));
        changePermission(op, permission, u);
        u = cacheService.saveUser(u);
        log.info("Saving new user permission settings: {}", u);
        permissionService.evict();
        return String.format("Modified user %s: %s permission %s", u.getName(), op.name().toLowerCase(), permission.getName());
    }

    private String editGuildPermission(Operation op, Permission permission, IGuild guild) {
        DiscordGuild g = cacheService.findGuildById(guild.getID()).orElseGet(() -> new DiscordGuild(guild));
        changePermission(op, permission, g);
        g = cacheService.saveGuild(g);
        log.info("Saving new guild permission settings: {}", g);
        permissionService.evict();
        return String.format("Modified server %s: %s permission %s", g.getName(), op.name().toLowerCase(), permission.getName());
    }

    private String editRolePermission(Operation op, Permission permission, IRole role) {
        IGuild parent = role.getGuild();
        DiscordGuild g = cacheService.findGuildById(parent.getID()).orElseGet(() -> new DiscordGuild(parent));
        DiscordRole edit = null;
        for (DiscordRole r : g.getRoles()) {
            if (r.getId().equals(role.getID())) {
                edit = r;
                break;
            }
        }
        if (edit == null) {
            edit = new DiscordRole(role);
            g.getRoles().add(edit);
        }
        if (edit.getName() == null) {
            edit.setName(role.getName());
        }
        changePermission(op, permission, edit);
        g = cacheService.saveGuild(g);
        log.info("Saving new guild/role permission settings: {}", g);
        permissionService.evict();
        return String.format("Modified role %s: %s permission %s", edit.getName(), op.name().toLowerCase(), permission.getName());
    }

    private String editChannelPermission(Operation op, Permission permission, IChannel channel) {
        if (channel.isPrivate()) {
            throw new IllegalArgumentException("No private channels allowed!");
        }
        DiscordGuild g = cacheService.findGuildById(channel.getGuild().getID()).orElseGet(() -> new DiscordGuild(channel.getGuild()));
        g = cacheService.saveGuild(g);
        DiscordChannel ch = cacheService.findChannelById(channel.getID()).orElseGet(() -> new DiscordChannel(channel));
        ch.setGuild(g);
        g.getChannels().add(ch);
        changePermission(op, permission, ch);
        ch = cacheService.saveChannel(ch);
        cacheService.saveGuild(g);
        log.info("Saving new permission data: {}", g);
        permissionService.evict();
        return String.format("Modified channel %s: %s permission %s", ch.getName(), op.name().toLowerCase(), permission.getName());
    }

    private enum Operation {
        ALLOW, DENY, RESET;
    }
}
