package com.ugcleague.ops.service;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.steam.packets.SteamPacket;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.repository.GameServerRepository;
import com.ugcleague.ops.web.websocket.dto.ConsoleMessage;
import com.ugcleague.ops.web.websocket.event.ConsoleAttachedEvent;
import com.ugcleague.ops.web.websocket.event.ConsoleDetachedEvent;
import com.ugcleague.ops.web.websocket.event.ConsoleMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Transactional
public class ConsoleService {
    private static final Logger log = LoggerFactory.getLogger(ConsoleService.class);

    private final GameServerService gameServerService;
    private final GameServerRepository gameServerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LeagueProperties leagueProperties;

    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final Map<String, String> addressMap = new ConcurrentHashMap<>();
    private Integer listenPort;

    @Autowired
    public ConsoleService(GameServerService gameServerService, GameServerRepository gameServerRepository,
                          ApplicationEventPublisher eventPublisher, LeagueProperties leagueProperties) {
        this.gameServerService = gameServerService;
        this.gameServerRepository = gameServerRepository;
        this.eventPublisher = eventPublisher;
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void configure() {
        listenPort = leagueProperties.getGameServers().getConsoleListenPort();
    }

    public GameServer checkedStart(GameServer server) {
        try {
            return start(server);
        } catch (MalformedURLException | SteamCondenserException | TimeoutException e) {
            log.warn("Could not start listening for logs", e);
        } catch (RemoteAccessException e) {
            log.warn("Invalid RCON password and could not retrieve the valid one");
        }
        return null;
    }

    private GameServer start(GameServer server)
        throws MalformedURLException, RemoteAccessException, SteamCondenserException, TimeoutException {
        if (listenPort == null) {
            listenPort = 7131;
        }
        URL ipCheckUrl = new URL("http://checkip.amazonaws.com");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ipCheckUrl.openStream()))) {
            String ip = reader.readLine();
            restartListener();
            gameServerService.rcon(server, Optional.empty(), "logaddress_add " + ip + ":" + listenPort);
            gameServerService.rcon(server, Optional.empty(), "logaddress_del " + ip + ":" + listenPort);
            gameServerService.rcon(server, Optional.empty(), "logaddress_add " + ip + ":" + listenPort);
            eventPublisher.publishEvent(new ConsoleAttachedEvent(server));
        } catch (IOException e) {
            log.warn("Could not read from IP check service: {}", e.toString());
        }
        return server;
    }

    private void restartListener() {
        if (!listening.get()) {
            CompletableFuture.runAsync(() -> listen());
        }
    }

    private void listen() {
        listening.set(true);
        try (DatagramSocket socket = new DatagramSocket(listenPort)) {
            byte[] buffer = new byte[1400]; // max size for a Source packet
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            log.info("Now listening for logs on port {}...", listenPort);
            while (true) {
                socket.receive(packet);
                if (isLogPacket(buffer)) {
                    String msg = new String(buffer, 5, packet.getLength() - 5, Charset.forName("UTF-8"));
                    String srcAddress = packet.getAddress().getHostAddress();
                    publishLine(srcAddress, msg.trim());
                } else {
                    String raw = new String(buffer, 0, packet.getLength(), Charset.forName("UTF-8"));
                    log.warn("Invalid data from {}: {}", packet.getSocketAddress().toString(), raw);
                }
                packet.setLength(buffer.length);
            }
        } catch (SocketException e) {
            log.warn("Could not setup listening socket: {}", e.toString());
        } catch (IOException e) {
            log.warn("Exception occurred while listening: {}", e.toString());
        } catch (Exception e) {
            log.error("Exception occurred while listening: {}", e);
        }
        log.info("Terminating log listener");
        listening.set(false);
    }

    private static boolean isLogPacket(byte[] bytes) {
        return (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFF && bytes[2] == (byte) 0xFF && bytes[3] == (byte) 0xFF
            && bytes[4] == SteamPacket.S2A_LOGSTRING_HEADER);
    }

    private void publishLine(String address, String line) {
        log.info("[{}] {}", address, line);
        ConsoleMessage message = new ConsoleMessage();
        message.setMessage(line);
        message.setUsername(getCachedServerAddress(address));
        eventPublisher.publishEvent(new ConsoleMessageEvent(message));
    }

    private String getCachedServerAddress(String address) {
        String value = addressMap.computeIfAbsent(address,
            k -> gameServerRepository.findByAddressStartingWith(k).map(GameServer::getAddress).orElse(null));
        if (value == null) {
            log.warn("Receiving logs from unknown server at {}", address);
            return address;
        }
        return value;
    }

    public GameServer checkedStop(GameServer server) {
        try {
            return stop(server);
        } catch (MalformedURLException | SteamCondenserException | TimeoutException e) {
            log.warn("Could not start listening for logs", e);
        } catch (RemoteAccessException e) {
            log.warn("Invalid RCON password and could not retrieve the valid one");
        }
        return server;
    }

    private GameServer stop(GameServer server)
        throws MalformedURLException, RemoteAccessException, SteamCondenserException, TimeoutException {
        URL ipCheckUrl = new URL("http://checkip.amazonaws.com");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ipCheckUrl.openStream()))) {
            String ip = reader.readLine();
            gameServerService.rcon(server, Optional.empty(), "logaddress_del " + ip + ":" + listenPort);
            eventPublisher.publishEvent(new ConsoleDetachedEvent(server));
        } catch (IOException e) {
            log.warn("Could not read from IP check service: {}", e.toString());
        }
        return server;
    }

    public String sendCommand(ConsoleMessage message, String address) {
        return gameServerRepository.findByAddress(address)
            .map(server -> gameServerService.rcon(server.getAddress(), message.getMessage()))
            .orElseThrow(() -> new IllegalArgumentException("Invalid destination"));
    }

}
