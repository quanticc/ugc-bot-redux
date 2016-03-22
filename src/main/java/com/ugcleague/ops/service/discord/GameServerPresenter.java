package com.ugcleague.ops.service.discord;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.steam.SteamPlayer;
import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.service.GameServerService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.DeadServerMap;
import com.ugcleague.ops.service.util.SourceServer;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.util.DateUtil.formatElapsed;
import static com.ugcleague.ops.util.DateUtil.formatRelative;
import static com.ugcleague.ops.util.Util.padLeft;
import static com.ugcleague.ops.util.Util.padRight;
import static java.util.Arrays.asList;

/**
 * Commands to perform operations over the registered game servers.
 * <ul>
 * <li>servers</li>
 * <li>server connect</li>
 * <li>server restart</li>
 * <li>server issues</li>
 * <li>server insecure</li>
 * <li>status</li>
 * <li>rcon</li>
 * </ul>
 */
@Service
public class GameServerPresenter {

    private static final Logger log = LoggerFactory.getLogger(GameServerPresenter.class);
    private static final String nonOptDesc = "multiple search by ID (chi1, dal5, mia3), address (68.115.23.245:27015) or" +
        " region groups (chicago, dallas, amsterdam). Also supports GS groups like claimed, unclaimed.";
    private static final ZonedDateTime EPOCH = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault());

    private final GameServerService gameServerService;
    private final CommandService commandService;

    private OptionSpec<Boolean> connectRconSpec;
    private OptionSpec<String> connectNonOptionSpec;
    private OptionSpec<String> statusNonOptionSpec;
    private OptionSpec<String> restartNonOptionSpec;
    private OptionSpec<String> rconNonOptionSpec;
    private OptionSpec<String> rconCommandSpec;
    private OptionSpec<String> rconPasswordSpec;
    private OptionSpec<String> insecureNonOptionSpec;
    private OptionSpec<Boolean> insecureValueSpec;
    private OptionSpec<Boolean> rconQuietSpec;

    @Autowired
    public GameServerPresenter(GameServerService gameServerService, CommandService commandService) {
        this.gameServerService = gameServerService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        initConnectCommand();
        initStatusCommand();
        initRestartCommand();
        initRconCommand();
        initDeadCommand();
        initInsecureCommand();
        commandService.register(CommandBuilder.equalsTo(".servers")
            .description("Show UGC game servers").support().permissionReplies()
            .queued().command((message, optionSet) -> gameServerService.findAll().stream()
                .map(g -> String.format("[**%s**] %s %s", g.getShortName(), g.getName(), g.getAddress()))
                .collect(Collectors.joining("\n"))).build());
    }

    private void initConnectCommand() {
        // .connect [-r] (non-option: search key)
        OptionParser parser = newParser();
        connectRconSpec = parser.acceptsAll(asList("r", "rcon"), "also display rcon_password")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        connectNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".server connect")
            .description("Show URL to join UGC game servers").support().permissionReplies()
            .parser(parser).queued().command(this::executeConnectCommand).build());
    }

    private String executeConnectCommand(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(connectNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            boolean rcon = o.has(connectRconSpec) && o.valueOf(connectRconSpec);
            StringBuilder message = new StringBuilder();
            List<GameServer> servers = gameServerService.findServersMultiple(nonOptions);
            if (servers.size() > 0) {
                message.append("Servers matching **").append(nonOptions.stream().collect(Collectors.joining(", "))).append("**\n");
                for (GameServer server : servers) {
                    String rconPassword = server.getRconPassword();
                    message.append("**").append(server.getName()).append("**: `")
                        .append(formatConnectString(server.getAddress(), server.getSvPassword())).append("` or ")
                        .append(formatSteamConnect(server.getAddress(), server.getSvPassword()));
                    if (rcon && rconPassword != null) {
                        message.append(" with `rcon_password ").append(rconPassword).append("`\n");
                    } else {
                        message.append("\n");
                    }
                }
            } else {
                message.append("No servers meet the criteria");
            }
            return message.toString();
        }
        return null;
    }

    private void initStatusCommand() {
        // .status (non-option: search key)
        OptionParser parser = newParser();
        statusNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".status")
            .description("Display info about a server").support().permissionReplies()
            .parser(parser).queued().command(this::executeStatusCommand).build());
    }

    private String executeStatusCommand(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(statusNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            Set<GameServer> matched = new LinkedHashSet<>();
            List<SourceServer> otherServers = new ArrayList<>();
            List<GameServer> found = gameServerService.findServersMultiple(nonOptions);
            if (found.size() > 0) {
                matched.addAll(found);
            } else {
                for (String key : nonOptions) {
                    try {
                        otherServers.add(new SourceServer(key));
                    } catch (SteamCondenserException e) {
                        log.warn("Could not add source server by address {}: {}", key, e.toString());
                    }
                }
            }
            StringBuilder message = new StringBuilder();
            for (GameServer server : matched) {
                server = gameServerService.refreshServerStatus(server);
                String srvId = gameServerService.toShortName(server);
                String version = "v" + server.getVersion();
                String claim = server.getExpireCheckDate().isEqual(EPOCH) ? "non-claimable" :
                    formatExpireDate(server.getExpireDate());
                String plyrs = server.getPing() > 0 ? server.getPlayers() + "/" + server.getMaxPlayers() : "**Down?**";
                String map = server.getMapName();
                int tvPort = server.getTvPort();
                String line = String.format("**%s**\t%s\t%s @ %s\t*%s*%s\n",
                    padRight(srvId, 5), padRight(version, 7), padLeft(plyrs, 5), padRight(map, 22), claim,
                    tvPort == 0 ? "" : " with SourceTV at port " + tvPort);
                message.append(line);
            }
            for (SourceServer server : otherServers) {
                Map<String, Object> map = gameServerService.refreshServerStatus(server);
                String plyrs = (int) map.get("ping") > 0 ? map.getOrDefault("players", "-") + "/" + map.getOrDefault("maxPlayers", "-") : "DOWN?";
                String mapName = (String) map.getOrDefault("mapName", "?");
                String tvPort = map.getOrDefault("tvPort", "?").toString();
                String line = String.format("**%s**\t%s\t%s @ %s\t%s\n",
                    server.toString(), map.getOrDefault("gameVersion", "vUnknown"),
                    plyrs, mapName, tvPort.equals("?") ? "" : " with SourceTV at port " + tvPort);
                message.append(line);
            }
            String rcon = null;
            GameServer server = null;
            SourceServer source = null;
            if (otherServers.size() == 1) {
                source = otherServers.get(0);
            }
            if (matched.size() == 1) {
                server = matched.stream().findFirst().get();
                rcon = gameServerService.refreshPasswordAndGet(server);
                source = gameServerService.getSourceServer(server);
            }
            // only do this if we matched exactly one server
            if (source != null) {
                try {
                    if (rcon != null) {
                        source.updatePlayers(rcon);
                    } else {
                        source.updatePlayers();
                    }
                    Map<String, SteamPlayer> playerMap = source.getPlayers();
                    if (playerMap.size() > 0) {
                        message.append("**Current players**\n");
                        playerMap.forEach((k, v) -> message.append(formatPlayer(v)).append("\n"));
                    }
                    message.append("**Server info**\n").append(source.getServerInfo().toString()).append("\n");
                } catch (SteamCondenserException | TimeoutException e) {
                    log.warn("Could not get player list: {}", e.toString());
                }
            }
            if (server != null && rcon != null) {
                message.append("**Connect info**\n`").append(formatConnectString(server.getAddress(), server.getSvPassword()))
                    .append("` or ").append(formatSteamConnect(server.getAddress(), server.getSvPassword()))
                    .append("\n`rcon_password ").append(rcon).append("`\n");
            }
            return message.toString();
        }
        return null;
    }

    private String formatPlayer(SteamPlayer p) {
        if (p.isExtended()) {
            return "#" + p.getRealId() + " **\"" + demarkdown(p.getName()) + "\"** " + p.getSteamId() +
                " connected from " + p.getIpAddress() + " for " + formatElapsed(p.getConnectTime());
        } else {
            return "#" + p.getId() + " **\"" + demarkdown(p.getName()) + "\"** connected for " + formatElapsed(p.getConnectTime());
        }
    }

    private String demarkdown(String input) {
        return input.replace("*", "\\*").replace("`", "\\`").replace("~", "\\~").replace("_", "\\_");
    }

    private String formatConnectString(String address, String password) {
        address = address.trim();
        String s = "connect " + address;
        if (password != null && !password.trim().isEmpty()) {
            s += ";password " + password;
        }
        return s;
    }

    private String formatSteamConnect(String address, String password) {
        address = address.trim();
        String s = "steam://connect/" + address + "/";
        if (password != null && !password.trim().isEmpty()) {
            s += password + "/";
        }
        return s;
    }

    private String formatExpireDate(ZonedDateTime expireDate) {
        return (expireDate.isBefore(ZonedDateTime.now()) ? "available since " : "claim expires ") +
            formatRelative(expireDate.toInstant());
    }

    private void initRestartCommand() {
        // .restart (non-option: search key)
        OptionParser parser = newParser();
        restartNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".server restart").support().permissionReplies()
            .description("Restart the given servers (only empty ones will be restarted)").queued().parser(parser)
            .command(this::executeRestartCommand).build());
    }

    private String executeRestartCommand(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(restartNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            StringBuilder message = new StringBuilder();
            List<GameServer> servers = gameServerService.findServersMultiple(nonOptions);
            if (servers.size() > 0) {
                message.append("Trying to restart servers matching **")
                    .append(nonOptions.stream().collect(Collectors.joining(", "))).append("**\n");
                for (GameServer server : servers) {
                    int code = gameServerService.attemptRestart(server);
                    if (code == 0) {
                        message.append("**").append(server.getName()).append("** restart in progress\n");
                    } else if (code > 0) {
                        message.append("**").append(server.getName()).append("** restart aborted. Players connected: ").append(code).append("\n");
                    } else {
                        message.append("**").append(server.getName()).append("** restart failed due to error\n");
                    }
                }
            } else {
                message.append("No servers meet the criteria");
            }
            return message.toString();
        }
        return null;
    }

    private void initRconCommand() {
        // .rcon -c <command> [-p <password>] (non-option: search key)
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        rconNonOptionSpec = parser.nonOptions(nonOptDesc + "\nCan also be invoked simply in the form: `.rcon <cmd> <server>`").ofType(String.class);
        rconCommandSpec = parser.acceptsAll(asList("c", "command"), "command to run via RCON").withRequiredArg();
        rconPasswordSpec = parser.acceptsAll(asList("p", "password"), "RCON password").withRequiredArg();
        rconQuietSpec = parser.acceptsAll(asList("q", "quiet"), "Don't output the command result to the channel")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        commandService.register(CommandBuilder.startsWith(".rcon")
            .description("Send a command to a game server using RCON").support().permissionReplies()
            .parser(parser).queued().command(this::executeRconCommand).build());
    }

    private String executeRconCommand(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(rconNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            String command;
            if (o.has(rconCommandSpec)) {
                command = o.valueOf(rconCommandSpec);
            } else {
                command = nonOptions.get(0);
                nonOptions = nonOptions.subList(1, nonOptions.size());
            }
            Set<GameServer> matched = new LinkedHashSet<>();
            List<SourceServer> otherServers = new ArrayList<>();
            List<GameServer> found = gameServerService.findServersMultiple(nonOptions);
            if (found.size() > 0) {
                matched.addAll(found);
            } else {
                for (String key : nonOptions) {
                    SourceServer socket = gameServerService.getSourceServer(key);
                    if (socket != null) {
                        otherServers.add(socket);
                    }
                }
            }
            StringBuilder message = new StringBuilder();
            boolean quiet = o.has(rconQuietSpec) && o.valueOf(rconQuietSpec)
                || (!o.has(rconQuietSpec) && command.contains("exec"));
            for (GameServer server : matched) {
                String password = o.has(rconPasswordSpec) ? o.valueOf(rconPasswordSpec) : server.getRconPassword();
                try {
                    message.append("**").append(gameServerService.toShortName(server)).append("** (")
                        .append(server.getAddress()).append("): ");
                    String result = gameServerService.rcon(server, Optional.of(password), command);
                    appendRconResult(message, command, result, quiet);
                } catch (TimeoutException e) {
                    message.append("Server is not responding");
                } catch (SteamCondenserException e) {
                    message.append("Error: ").append(e.getMessage());
                }
            }
            for (SourceServer server : otherServers) {
                if (o.has(rconPasswordSpec)) {
                    try {
                        String password = o.valueOf(rconPasswordSpec);
                        message.append("**").append(server.toString()).append("**: ");
                        String result = gameServerService.rcon(server, password, command);
                        appendRconResult(message, command, result, quiet);
                    } catch (TimeoutException e) {
                        message.append("Server is not responding");
                    } catch (SteamCondenserException e) {
                        message.append("Error: ").append(e.getMessage());
                    }
                } else {
                    log.info("Ignoring 'rcon {}' to server {} since no password was given", command, server.toString());
                }
            }
            return message.toString();
        }
        return null;
    }

    private void appendRconResult(StringBuilder message, String command, String result, boolean quiet) {
        if (quiet) {
            result = "Executed ``rcon " + command + "`` command\n";
            message.append(result);
        } else {
            if (command.trim().replace("\"", "").equals("status")) {
                message.append("```\n").append(result).append("\n```\n");
            } else {
                message.append(result).append("\n");
            }
        }
    }

    private void initDeadCommand() {
        commandService.register(CommandBuilder.equalsTo(".server issues")
            .description("Display unresponsive or outdated UGC game servers").support().permissionReplies()
            .command(this::executeDeadCommand).build());
    }

    private String executeDeadCommand(IMessage message, OptionSet optionSet) {
        DeadServerMap map = gameServerService.getDeadServerMap();
        int failedAttempts = 2;
        String nonResponsive = map.entrySet().stream()
            .filter(e -> e.getValue().getAttempts().get() > failedAttempts)
            .map(e -> String.format("• **%s** (%s) appears to be down since %s",
                e.getKey().getShortName(), e.getKey().getAddress(),
                formatRelative(e.getValue().getCreated())))
            .collect(Collectors.joining("\n"));
        if (!nonResponsive.isEmpty()) {
            nonResponsive += "\nRestart with `.server restart " + map.entrySet().stream()
                .filter(e -> e.getValue().getAttempts().get() > failedAttempts)
                .map(e -> e.getKey().getShortName()).collect(Collectors.joining(" ")) + "`\n";
        }
        String outdated = gameServerService.findOutdatedServers().stream()
            .map(s -> String.format("• **%s** (%s) has an older game version (v%d)",
                s.getShortName(), s.getAddress(), s.getVersion()))
            .collect(Collectors.joining("\n"));
        return nonResponsive.isEmpty() && outdated.isEmpty() ? ":ok_hand: All game servers are OK" :
            "*Game servers with issues*\n" + nonResponsive + "\n" + outdated;
    }

    private void initInsecureCommand() {
        // .server insecure -v <true|false> <non-options: search key>
        OptionParser parser = newParser();
        insecureNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        insecureValueSpec = parser.acceptsAll(asList("v", "value"), "value to set as the insecure state")
            .withRequiredArg().required().ofType(Boolean.class);
        commandService.register(CommandBuilder.startsWith(".server insecure")
            .description("Set or remove the insecure FTP mode on a GS server").master()
            .parser(parser).command(this::executeInsecureCommand).build());
    }

    private String executeInsecureCommand(IMessage m, OptionSet o) {
        List<String> nonOptions = o.valuesOf(insecureNonOptionSpec);
        if (!o.has("?") && !nonOptions.isEmpty()) {
            boolean value = o.valueOf(insecureValueSpec);
            StringBuilder response = new StringBuilder();
            response.append("*Insecure flags updated*\n");
            List<GameServer> found = gameServerService.findServersMultiple(nonOptions);
            for (GameServer server : found) {
                boolean previouslySecure = gameServerService.isSecure(server);
                boolean secure = value ? gameServerService.isSecure(gameServerService.addInsecureFlag(server)) :
                    gameServerService.isSecure(gameServerService.removeInsecureFlag(server));
                if (previouslySecure != secure) {
                    response.append("**").append(server.getShortName()).append("** ")
                        .append(previouslySecure).append(" -> ").append(secure).append("\n");
                } else {
                    response.append("**").append(server.getShortName()).append("** ")
                        .append(secure).append(" (no change)").append("\n");
                }
            }
            return response.toString();
        }
        return null;
    }
}
