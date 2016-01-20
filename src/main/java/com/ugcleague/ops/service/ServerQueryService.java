package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.event.DiscordReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.MessageBuilder;

import java.util.List;

@Service
public class ServerQueryService {

    private static final Logger log = LoggerFactory.getLogger(ServerQueryService.class);

    private final DiscordService discordService;
    private final GameServerService gameServerService;

    @Autowired
    public ServerQueryService(DiscordService discordService, GameServerService gameServerService) {
        this.discordService = discordService;
        this.gameServerService = gameServerService;
    }

    @EventListener
    private void onReady(DiscordReadyEvent event) {
        discordService.subscribe(new IListener<MessageReceivedEvent>() {

            @Override
            public void handle(MessageReceivedEvent event) {
                IMessage m = event.getMessage();
                if (m.getContent().startsWith(".connect ")) {
                    String arg = m.getContent().split(" ", 2)[1];
                    connectInfo(m.getAuthor(), arg);
                } /*else if (m.getContent().startsWith(".s ") || m.getContent().startsWith(".status ")) {
                    String arg = m.getContent().split(" ", 2)[1];
                    serverStatus(m.getAuthor(), arg);
                }*/
            }
        });
    }

    private void connectInfo(IUser user, String key) {
        // only to authorized users
        if (discordService.hasSupportRole(user)) {
            try {
                List<GameServer> servers = gameServerService.findServers(key);
                MessageBuilder message = discordService.privateMessage(user);
                if (servers.size() > 0) {
                    message.appendContent("```\n");
                    for (GameServer server : servers) {
                        message.appendContent(server.getName() + ": steam://connect/" +
                            server.getAddress() + "/" + formatNullEmpty(server.getSvPassword()) + "\n");
                    }
                    message.appendContent("```").send();
                } else {
                    message.appendContent("No servers meet the criteria").send();
                }
            } catch (Exception e) {
                log.warn("Could not give connect info to {}: {}", user, e.toString());
            }
        }
    }

    private String formatNullEmpty(String str) {
        return str == null ? "" : str.trim();
    }
}
