package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.document.*;
import com.ugcleague.ops.domain.util.PermissionProvider;
import com.ugcleague.ops.repository.mongo.DiscordChannelRepository;
import com.ugcleague.ops.repository.mongo.DiscordGuildRepository;
import com.ugcleague.ops.repository.mongo.DiscordUserRepository;
import com.ugcleague.ops.repository.mongo.PermissionRepository;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.impl.obj.User;
import sx.blah.discord.handle.obj.*;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;

@Service
@Transactional
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final LeagueProperties properties;
    private final DiscordService discordService;
    private final CommandService commandService;
    private final PermissionRepository permissionRepository;
    private final DiscordGuildRepository guildRepository;
    private final DiscordChannelRepository channelRepository;
    private final DiscordUserRepository userRepository;
    private final IUser anyone = anyone();

    private OptionSpec<String> permNonOptionSpec;

    @Autowired
    public PermissionService(LeagueProperties properties, DiscordService discordService,
                             CommandService commandService, PermissionRepository permissionRepository,
                             DiscordGuildRepository guildRepository, DiscordChannelRepository channelRepository,
                             DiscordUserRepository userRepository) {
        this.properties = properties;
        this.discordService = discordService;
        this.commandService = commandService;
        this.permissionRepository = permissionRepository;
        this.guildRepository = guildRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
    }

    private IUser anyone() {
        return new User(null, "anyone", "0", "0", "", Presences.OFFLINE);
    }

    @PostConstruct
    private void configure() {
        if (permissionRepository.count() == 0) {
            log.debug("Performing permission first-time setup");

            // convert from .yml to persistent store
            Permission supportPublish = permissionRepository.save(new Permission("support.publish")); // can emit support pings
            Permission commandMaster = permissionRepository.save(new Permission("command.master")); // can execute master commands
            Permission commandSupport = permissionRepository.save(new Permission("command.support")); // can execute support commands
            permissionRepository.save(new Permission("command.user", true)); // can execute user commands

            LeagueProperties.Discord.Support support = properties.getDiscord().getSupport();

            // The guild initially configured for support
            String guildId = support.getGuild();
            DiscordGuild guild = guildRepository.findOne(guildId);
            if (guild == null) {
                guild = new DiscordGuild();
                guild.setId(guildId);
            }
            guild = guildRepository.save(guild);

            // Channels that emit support events
            for (String channelId : support.getChannels()) {
                DiscordChannel channel = channelRepository.findOne(channelId);
                if (channel == null) {
                    channel = new DiscordChannel();
                    channel.setId(channelId);
                }
                channel.getAllowed().add(supportPublish);
                channelRepository.save(channel);
            }

            // Roles that can sub to support events, therefore should be excluded from emitting them
            for (String roleId : support.getRoles()) {
                DiscordRole role = new DiscordRole(roleId);
                role.getDenied().add(supportPublish);
                if (!guild.getRoles().contains(role)) {
                    guild.getRoles().add(role);
                }
            }

            // Roles that can't emit support events but also can't subscribe to them
            for (String roleId : support.getExcludedRoles()) {
                DiscordRole role = new DiscordRole(roleId);
                if (!guild.getRoles().contains(role)) {
                    guild.getRoles().add(role);
                }
                role.getDenied().add(supportPublish);
            }

            LeagueProperties.Discord discord = properties.getDiscord();

            String masterId = discord.getMaster();
            DiscordUser user = userRepository.findOne(masterId);
            if (user == null) {
                user = new DiscordUser();
                user.setId(masterId);
            }
            user.getAllowed().add(commandMaster);
            user.getAllowed().add(commandSupport);
            userRepository.save(user);

            // this is for command reply execution but also visibility
            for (Map.Entry<String, String> entry : discord.getChannels().entrySet()) {
                String channelId = entry.getKey();
                DiscordChannel channel = channelRepository.findOne(channelId);
                if (channel == null) {
                    channel = new DiscordChannel();
                    channel.setId(channelId);
                }
                String level = entry.getValue();
                if (level.equals("master")) {
                    channel.getAllowed().add(commandMaster);
                    channel.getAllowed().add(commandSupport);
                } else if (level.equals("support")) {
                    channel.getAllowed().add(commandSupport);
                }
                channelRepository.save(channel);
            }

            guildRepository.save(guild);
        }
        log.debug("Permissions: {}", permissionRepository.findAll());
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
            .master().originReplies().mention().command(this::managePermissions).build());
    }

    /**
     * Is this channel allowed to display this command's result?
     *
     * @param command invoked command
     * @param channel command location
     * @return <code>true</code> if the channel is allowed to display this command's result, <code>false</code> otherwise.
     */
    public boolean canDisplayResult(Command command, IChannel channel) {
        return canDisplayResult(command.getPermission().getKey(), channel);
    }

    /**
     * Is this channel allowed to perform this operation?
     *
     * @param permissionName requested permission name
     * @param channel        action location
     * @return <code>true</code> if the user is allowed to execute the command, <code>false</code> otherwise.
     */
    private boolean canDisplayResult(String permissionName, IChannel channel) {
        // private channels can always display a command result
        // but this should not be called on private channels (without user check)
        return channel.isPrivate() || canPerform(permissionName, anyone, channel);
    }

    /**
     * Is this user allowed to execute this command in this channel?
     *
     * @param command invoked command
     * @param user    command invoker
     * @param channel command location
     * @return <code>true</code> if the user is allowed to execute the command, <code>false</code> otherwise.
     */
    public boolean canExecute(Command command, IUser user, IChannel channel) {
        return canPerform(command.getPermission().getKey(), user, channel);
    }

    /**
     * Is this user allowed to do this operation in this channel?
     *
     * @param permissionName requested permission name
     * @param user           action invoker
     * @param channel        action location
     * @return <code>true</code> if the user is allowed to perform the action, <code>false</code> otherwise.
     */
    @Cacheable("permissions")
    public boolean canPerform(String permissionName, IUser user, IChannel channel) {
        if (channel.isPrivate()) {
            for (IGuild guild : discordService.getClient().getGuilds()) {
                if (canPerform(permissionName, user, channel, guild)) {
                    return true;
                }
            }
            return false;
        } else {
            return canPerform(permissionName, user, channel, channel.getGuild());
        }
    }

    private boolean canPerform(String permissionName, IUser user, IChannel channel, IGuild guild) {
        Optional<Permission> p = permissionRepository.findByName(permissionName);
        if (!p.isPresent()) {
            log.warn("Permission with key '{}' is not defined", permissionName);
            return false;
        }
        Permission permission = p.get();
        Set<Permission> allowed = permissionSet(user, channel, guild, PermissionProvider::getAllowed);
        Set<Permission> denied = permissionSet(user, channel, guild, PermissionProvider::getDenied);
        return !denied.contains(permission) && ((allowed.contains(permission)) || permission.isDefaultAllow());
    }

    private Set<Permission> permissionSet(IUser user, IChannel channel, IGuild guild, Function<PermissionProvider, Set<Permission>> mapper) {
        Set<Permission> set = new HashSet<>();

        // collect bot operation permissions from the given user
        DiscordUser cachedUser = userRepository.findOne(user.getID());
        if (cachedUser == null) {
            cachedUser = new DiscordUser();
            cachedUser.setId(user.getID());
            cachedUser.setName(user.getName());
            cachedUser = userRepository.save(cachedUser);
        }
        if (cachedUser.getName() == null) {
            cachedUser.setName(user.getName());
            cachedUser = userRepository.save(cachedUser);
        }
        set.addAll(mapper.apply(cachedUser));

        // collect bot operation permissions from the given guild
        DiscordGuild cachedGuild = guildRepository.findOne(guild.getID());
        if (cachedGuild == null) {
            cachedGuild = new DiscordGuild();
            cachedGuild.setId(guild.getID());
            cachedGuild.setName(guild.getName());
            cachedGuild = guildRepository.save(cachedGuild);
        }
        if (cachedGuild.getName() == null) {
            cachedGuild.setName(guild.getName());
            cachedGuild = guildRepository.save(cachedGuild);
        }
        set.addAll(mapper.apply(cachedGuild));

        // collect bot operation permissions from the roles of the given user in the given guild
        List<IRole> roles = user.getRolesForGuild(guild.getID());
        Set<DiscordRole> cachedRoles = cachedGuild.getRoles();
        boolean rolesModified = false;
        for (IRole role : roles) {
            DiscordRole cachedRole = null;
            for (DiscordRole innerRole : cachedRoles) {
                if (role.getID().equals(innerRole.getId())) {
                    cachedRole = innerRole;
                    break;
                }
            }
            if (cachedRole == null) {
                cachedRole = new DiscordRole(role.getID());
                cachedRole.setName(role.getName());
                cachedRoles.add(cachedRole);
                rolesModified = true;
            }
            set.addAll(mapper.apply(cachedRole));
        }
        if (rolesModified) {
            guildRepository.save(cachedGuild);
        }

        // collect bot operation permissions from the given channel
        DiscordChannel cachedChannel = channelRepository.findOne(channel.getID());
        if (cachedChannel == null) {
            cachedChannel = new DiscordChannel();
            cachedChannel.setId(channel.getID());
            cachedChannel.setName(channel.getName());
            cachedChannel.setPrivate(channel.isPrivate());
            cachedChannel = channelRepository.save(cachedChannel);
        }
        if (cachedChannel.getName() == null) {
            cachedChannel.setName(channel.getName());
            cachedChannel = channelRepository.save(cachedChannel);
        }
        set.addAll(mapper.apply(cachedChannel));

        return set;
    }

    private String managePermissions(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(permNonOptionSpec);
        if (optionSet.has("?") || nonOptions.size() < 3) {
            return null;
        }
        String permName = nonOptions.get(0).toLowerCase();
        String action = nonOptions.get(1).toLowerCase();
        Optional<Permission> permission = permissionRepository.findByName(permName);
        List<String> args = nonOptions.subList(2, nonOptions.size());
        if (action.equals("list")) {
            listPermissions(message, permName, args);
        } else if (action.equals("evict")) {
            evict();
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
        return "Invalid operation";
    }

    private String listPermissions(IMessage message, String permName, List<String> args) {
        return permissionRepository.findAll().stream()
            .filter(p -> p.getName().contains(permName))
            .map(p -> listPermissions(message, p, args))
            .collect(Collectors.joining("\n"));
    }

    private String listPermissions(IMessage message, Permission permission, List<String> args) {
        List<String> matches = new ArrayList<>();
        // TODO narrow search by following args
        for (DiscordGuild guild : guildRepository.findAll()) {
            if (guild.getDenied().contains(permission)) {
                matches.add(String.format("deny everyone in guild %s (%s)", guild.getName(), guild.getId()));
            } else if (guild.getAllowed().contains(permission)) {
                matches.add(String.format("allow anyone in guild %s (%s)", guild.getName(), guild.getId()));
            }
            for (DiscordRole role : guild.getRoles()) {
                if (role.getDenied().contains(permission)) {
                    matches.add(String.format("deny everyone with role %s (%s) in guild %s (%s)", role.getName(), role.getId(), guild.getName(), guild.getId()));
                } else if (role.getAllowed().contains(permission)) {
                    matches.add(String.format("allow anyone with role %s (%s) in guild %s (%s)", role.getName(), role.getId(), guild.getName(), guild.getId()));
                }
            }
        }
        for (DiscordChannel channel : channelRepository.findAll()) {
            if (channel.getDenied().contains(permission)) {
                matches.add(String.format("deny everyone in channel %s (%s)", channel.getName(), channel.getId()));
            } else if (channel.getAllowed().contains(permission)) {
                matches.add(String.format("allow anyone in channel %s (%s)", channel.getName(), channel.getId()));
            }
        }
        for (DiscordUser user : userRepository.findAll()) {
            if (user.getDenied().contains(permission)) {
                matches.add(String.format("deny user %s (%s)", user.getName(), user.getId()));
            } else if (user.getAllowed().contains(permission)) {
                matches.add(String.format("allow user %s (%s)", user.getName(), user.getId()));
            }
        }
        if (permission.isDefaultAllow()) {
            matches.add("allow if not previously denied");
        }
        return matches.stream().collect(Collectors.joining(", "));
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
        }
        return ":no_entry:";
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
        DiscordUser u = userRepository.findById(user.getID()).orElseGet(() -> new DiscordUser(user));
        changePermission(op, permission, u);
        u = userRepository.save(u);
        log.info("Saving new permission data: {}", u);
        evict();
        return String.format("Modified user %s: %s permission %s", u.getName(), op.name().toLowerCase(), permission.getName());
    }

    private String editGuildPermission(Operation op, Permission permission, IGuild guild) {
        DiscordGuild g = guildRepository.findById(guild.getID()).orElseGet(() -> new DiscordGuild(guild));
        changePermission(op, permission, g);
        g = guildRepository.save(g);
        log.info("Saving new permission data: {}", g);
        evict();
        return String.format("Modified server %s: %s permission %s", g.getName(), op.name().toLowerCase(), permission.getName());
    }

    private String editRolePermission(Operation op, Permission permission, IRole role) {
        IGuild parent = role.getGuild();
        DiscordGuild g = guildRepository.findById(parent.getID()).orElseGet(() -> new DiscordGuild(parent));
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
        changePermission(op, permission, edit);
        g = guildRepository.save(g);
        log.info("Saving new permission data: {}", g);
        evict();
        return String.format("Modified role %s: %s permission %s", edit.getName(), op.name().toLowerCase(), permission.getName());
    }

    private String editChannelPermission(Operation op, Permission permission, IChannel channel) {
        if (channel.isPrivate()) {
            throw new IllegalArgumentException("No private channels allowed!");
        }
        DiscordGuild g = guildRepository.findById(channel.getGuild().getID()).orElseGet(() -> new DiscordGuild(channel.getGuild()));
        g = guildRepository.save(g);
        DiscordChannel ch = channelRepository.findById(channel.getID()).orElseGet(() -> new DiscordChannel(channel));
        ch.setGuild(g);
        g.getChannels().add(ch);
        changePermission(op, permission, ch);
        ch = channelRepository.save(ch);
        guildRepository.save(g);
        log.info("Saving new permission data: {}", g);
        evict();
        return String.format("Modified channel %s: %s permission %s", ch.getName(), op.name().toLowerCase(), permission.getName());
    }

    @CacheEvict(cacheNames = "permissions", allEntries = true)
    public void evict() {
        log.info("Permission cache invalidated for ALL entries");
    }

    @CacheEvict(cacheNames = "permissions")
    public void evict(String permissionName, IUser user, IChannel channel) {
        log.info("Permission cache invalidated for: ({}, {}, {})", permissionName, user.getID(), channel.getID());
    }

    private enum Operation {
        ALLOW, DENY, RESET;
    }

}
