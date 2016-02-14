package com.ugcleague.ops.service;

import com.codahale.metrics.annotation.Timed;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.document.*;
import com.ugcleague.ops.domain.util.PermissionProvider;
import com.ugcleague.ops.repository.mongo.PermissionRepository;
import com.ugcleague.ops.service.discord.DiscordCacheService;
import com.ugcleague.ops.service.discord.command.Command;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.impl.obj.User;
import sx.blah.discord.handle.obj.*;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
@Transactional
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final LeagueProperties properties;
    private final DiscordService discordService;
    private final PermissionRepository permissionRepository;
    private final DiscordCacheService cacheService;
    private final IUser anyone = anyone();
    private final Map<Triple<String, String, String>, Boolean> permissionCache = new ConcurrentHashMap<>();

    @Autowired
    public PermissionService(LeagueProperties properties, DiscordService discordService,
                             PermissionRepository permissionRepository, DiscordCacheService cacheService) {
        this.properties = properties;
        this.discordService = discordService;
        this.permissionRepository = permissionRepository;
        this.cacheService = cacheService;
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
            DiscordGuild guild = cacheService.findGuildById(guildId).orElseGet(() -> new DiscordGuild(guildId));
            guild = cacheService.saveGuild(guild);

            // Channels that emit support events
            for (String channelId : support.getChannels()) {
                DiscordChannel channel = cacheService.findChannelById(channelId).orElseGet(() -> new DiscordChannel(channelId));
                channel.getAllowed().add(supportPublish);
                cacheService.saveChannel(channel);
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
            DiscordUser user = cacheService.findUserById(masterId).orElseGet(() -> new DiscordUser(masterId));
            user.getAllowed().add(commandMaster);
            user.getAllowed().add(commandSupport);
            cacheService.saveUser(user);

            // this is for command reply execution but also visibility
            for (Map.Entry<String, String> entry : discord.getChannels().entrySet()) {
                String channelId = entry.getKey();
                DiscordChannel channel = cacheService.findChannelById(channelId).orElseGet(() -> new DiscordChannel(channelId));
                String level = entry.getValue();
                if (level.equals("master")) {
                    channel.getAllowed().add(commandMaster);
                    channel.getAllowed().add(commandSupport);
                } else if (level.equals("support")) {
                    channel.getAllowed().add(commandSupport);
                }
                cacheService.saveChannel(channel);
            }

            cacheService.saveGuild(guild);
        }
        log.debug("Permissions: {}", permissionRepository.findAll());
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
    @Timed
    public boolean canPerform(String permissionName, IUser user, IChannel channel) {
        Triple<String, String, String> key = Triple.of(permissionName, user.getID(), channel.getID());
        Boolean cached = permissionCache.get(key);
        if (cached != null) {
            return cached;
        } else {
            boolean result = slowCanPerform(permissionName, user, channel);
            log.debug("Caching result of {} -> {}", key, result);
            permissionCache.put(key, result);
            return result;
        }
    }

    private boolean slowCanPerform(String permissionName, IUser user, IChannel channel) {
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
        DiscordUser cachedUser = cacheService.findUserById(user.getID()).orElseGet(() -> cacheService.saveUser(new DiscordUser(user)));
        if (cachedUser.getName() == null) {
            cachedUser.setName(user.getName());
            cachedUser = cacheService.saveUser(cachedUser);
        }
        set.addAll(mapper.apply(cachedUser));

        // collect bot operation permissions from the given guild
        DiscordGuild cachedGuild = cacheService.findGuildById(guild.getID()).orElseGet(() -> cacheService.saveGuild(new DiscordGuild(guild)));
        if (cachedGuild.getName() == null) {
            cachedGuild.setName(guild.getName());
            cachedGuild = cacheService.saveGuild(cachedGuild);
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
            cacheService.saveGuild(cachedGuild);
        }

        // collect bot operation permissions from the given channel
        DiscordChannel cachedChannel = cacheService.findChannelById(channel.getID()).orElseGet(() -> cacheService.saveChannel(new DiscordChannel(channel)));
        if (cachedChannel == null) {
            cachedChannel = new DiscordChannel();
            cachedChannel.setId(channel.getID());
            cachedChannel.setName(channel.getName());
            cachedChannel.setPrivate(channel.isPrivate());
            cachedChannel = cacheService.saveChannel(cachedChannel);
        }
        if (cachedChannel.getName() == null) {
            cachedChannel.setName(channel.getName());
            cachedChannel = cacheService.saveChannel(cachedChannel);
        }
        set.addAll(mapper.apply(cachedChannel));

        return set;
    }

    @Scheduled(cron = "0 0 5 * * *") // 5am every day
    public void evict() {
        log.info("Permission cache invalidated for ALL entries");
        permissionCache.clear();
    }

    public Optional<Permission> findPermissionByName(String name) {
        return permissionRepository.findByName(name);
    }

    public List<Permission> findAllPermissions() {
        return permissionRepository.findAll();
    }
}
