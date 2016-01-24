package com.ugcleague.ops.service.discord;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.steam.SteamPlayer;
import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.service.GameServerService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.util.SourceServer;
import com.ugcleague.ops.util.DateUtil;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.obj.IMessage;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

@Service
public class ServerQueryService {

    private static final Logger log = LoggerFactory.getLogger(ServerQueryService.class);
    private static final String nonOptDesc = "multiple search by ID, address or groups like chicago, dallas, etc. " +
        "Also support groups like claimed, unclaimed.";

    private final GameServerService gameServerService;
    private final CommandService commandService;

    private OptionSpec<Boolean> connectRconSpec;
    private OptionSpec<String> connectNonOptionSpec;
    private OptionSpec<String> statusNonOptionSpec;
    private OptionSpec<String> restartNonOptionSpec;

    @Autowired
    public ServerQueryService(GameServerService gameServerService, CommandService commandService) {
        this.gameServerService = gameServerService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        initConnectCommand();
        initStatusCommand();
        initRestartCommand();
    }

    private void initConnectCommand() {
        // .connect [-r] (non-option: search key)
        OptionParser parser = new OptionParser();
        parser.posixlyCorrect(true);
        connectRconSpec = parser.acceptsAll(asList("r", "rcon"), "also display rcon_password")
            .withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        connectNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".connect")
            .description("Shows URL to join UGC game servers").permission("support")
            .parser(parser).command(this::executeConnectCommand).build());
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
                    message.append("**").append(server.getName()).append("**: steam://connect/")
                        .append(server.getAddress()).append("/").append(formatNullEmpty(server.getSvPassword()));
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
        OptionParser parser = new OptionParser();
        parser.posixlyCorrect(true);
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        statusNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".status")
            .description("Displays info about a server").permission("support")
            .parser(parser).command(this::executeStatusCommand).build());
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
                String claim = formatDuration(Duration.between(ZonedDateTime.now(), server.getExpireDate()));
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
                    server.getHostNames().get(0), map.getOrDefault("gameVersion", "vUnknown"),
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
                rcon = server.getRconPassword();
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
                message.append("**Connect info**\n").append("steam://connect/")
                    .append(server.getAddress()).append("/").append(formatNullEmpty(server.getSvPassword()))
                    .append("\n").append("`rcon_password ").append(rcon).append("`\n");
            }
            return message.toString();
        }
        return null;
    }

    private String formatPlayer(SteamPlayer p) {
        if (p.isExtended()) {
            return "#" + p.getRealId() + " **\"" + p.getName() + "\"** " + p.getSteamId() +
                ", IP: " + p.getIpAddress() + ", Time: " + formatHoursMinutes(p.getConnectTime());
        } else {
            return "#" + p.getId() + " **\"" + p.getName() + "\"**, Time: " + formatHoursMinutes(p.getConnectTime());
        }
    }

    private String formatNullEmpty(String str) {
        return str == null ? "" : str.trim();
    }

    private String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    private String padLeft(String s, int n) {
        return String.format("%1$" + n + "s", s);
    }

    private String formatDuration(Duration duration) {
        return (duration.isNegative() ? "claim expired " : "claim expires ") + DateUtil.formatRelative(duration);
    }

    private String formatHoursMinutes(float seconds) {
        long totalSeconds = (long) seconds;
        return String.format(
            "%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60);
    }

    private void initRestartCommand() {
        // .restart (non-option: search key)
        OptionParser parser = new OptionParser();
        parser.posixlyCorrect(true);
        parser.acceptsAll(asList("?", "h", "help"), "display the help").forHelp();
        restartNonOptionSpec = parser.nonOptions(nonOptDesc).ofType(String.class);
        commandService.register(CommandBuilder.startsWith(".restart")
            .description("Restart the given servers. Only empty ones will be restarted").permission("support")
            .parser(parser).command(this::executeRestartCommand).build());
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
}
