package com.ugcleague.ops.service;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.event.GameUpdateCompletedEvent;
import com.ugcleague.ops.event.GameUpdateFailedEvent;
import com.ugcleague.ops.event.GameUpdateStartedEvent;
import com.ugcleague.ops.event.NewGameVersionAvailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.DiscordInstantiationException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.Presences;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class DiscordService {

    private static final Logger log = LoggerFactory.getLogger(DiscordService.class);

    private final LeagueProperties leagueProperties;
    private IDiscordClient client;

    private Instant lastFailedBroadcast = Instant.EPOCH;

    @Autowired
    public DiscordService(LeagueProperties leagueProperties) {
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void configure() {
        if (leagueProperties.getDiscord().isAutologin()) {
            CompletableFuture.runAsync(() -> sleep(10000)).thenRun(this::tryLogin);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void tryLogin() {
        try {
            login();
        } catch (DiscordInstantiationException e) {
            e.printStackTrace();
        }
    }

    public void login() throws DiscordInstantiationException {
        String email = leagueProperties.getDiscord().getEmail();
        String password = leagueProperties.getDiscord().getPassword();
        client = new ClientBuilder().withLogin(email, password).login();
        client.getDispatcher().registerListener(new IListener<ReadyEvent>() {
            @Override
            public void handle(ReadyEvent readyEvent) {
                log.info("*** Discord bot armed ***");
                List<Guild> guildList = client.getGuilds();
                for (Guild guild : guildList) {
                    log.info("{}", DiscordService.toString(guild));
                }
                for (String inviteCode : leagueProperties.getDiscord().getInvites()) {
                    Invite invite = client.getInviteForCode(inviteCode);
                    try {
                        Invite.InviteResponse response = invite.details();
                        log.info("Accepting invite to {} ({}) @ {} ({})",
                            response.getChannelName(), response.getChannelID(), response.getGuildName(), response.getGuildID());
                        invite.accept();
                    } catch (Exception e) {
                        log.warn("Could not accept invite {}: {}", inviteCode, e.toString());
                    }
                }
            }
        });
        client.getDispatcher().registerListener(new IListener<MessageReceivedEvent>() {
            @Override
            public void handle(MessageReceivedEvent messageReceivedEvent) {
                Message m = messageReceivedEvent.getMessage();
                if (m.getContent().startsWith(".clear")) {
                    Channel c = client.getChannelByID(m.getChannel().getID());
                    if (null != c) {
                        c.getMessages().stream().filter(message -> message.getAuthor().getID()
                            .equalsIgnoreCase(client.getOurUser().getID())).forEach(message -> {
                            try {
                                log.debug("Attempting deletion of message {} by \"{}\" ({})", message.getID(), message.getAuthor().getName(), message.getContent());
                                client.deleteMessage(message.getID(), message.getChannel().getID());
                            } catch (IOException e) {
                                log.error("Couldn't delete message {} ({}).", message.getID(), e.getMessage());
                            }
                        });
                    }
                } else if (m.getContent().startsWith(".name ")) {
                    if (isMaster(m.getAuthor())) {
                        String s = m.getContent().split(" ", 2)[1];
                        try {
                            client.changeAccountInfo(s, "", "", IDiscordClient.Image.forUser(client.getOurUser()));
                            m.reply("is this better?");
                        } catch (IOException | URISyntaxException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (m.getContent().startsWith(".pm")) {
                    try {
                        PrivateChannel channel = client.getOrCreatePMChannel(m.getAuthor());
                        new MessageBuilder(client).withChannel(channel).withContent(randomMessage()).build();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (m.getContent().startsWith(".game")) {
                    if (isMaster(m.getAuthor())) {
                        String game = m.getContent().length() > 6 ? m.getContent().substring(6) : null;
                        client.updatePresence(client.getOurUser().getPresence().equals(Presences.IDLE),
                            Optional.ofNullable(game));
                    }
                }
            }
        });
    }

    public boolean isMaster(String id) {
        return leagueProperties.getDiscord().getMasters().contains(id);
    }

    public boolean isMaster(User user) {
        return isMaster(user.getID());
    }

    public void send(String channelNameRegex, String message) {
        client.getGuilds().stream()
            .flatMap(g -> g.getChannels().stream())
            .filter(ch -> ch.getName().matches(channelNameRegex))
            .forEach(channel -> channel.sendMessage(message));
    }

    public void broadcast(String message) {
        client.getGuilds().stream()
            .flatMap(g -> g.getChannels().stream())
            .forEach(ch -> ch.sendMessage(message));
    }

    public IDiscordClient getClient() {
        return client;
    }

    // utilities

    public static String randomMessage() {
        String result = "SUP DUDE";
        try {
            URL url = new URL("http://whatthecommit.com/index.txt");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                result = reader.readLine();
            } catch (IOException e) {
                log.warn("Could not get a random message: {}", e.toString());
            }
        } catch (MalformedURLException ignored) {
        }
        return result;
    }

    public static String toString(Guild guild) {
        String id = guild.getID();
        String name = guild.getName();
        User owner = guild.getOwner();
        List<Channel> channels = guild.getChannels();
        return "Guild ["
            + "id='" + id
            + "', name='" + name
            + "', owner='" + toString(owner)
            + ", channels=[" + channels.stream().map(DiscordService::toString).collect(Collectors.joining(", "))
            + "]]";
    }

    public static String toString(Channel channel) {
        String id = channel.getID();
        String name = channel.getName();
        String topic = channel.getTopic();
        return "Channel ["
            + "id='" + id
            + "', name='" + name
            + "', topic='" + topic
            + "']";
    }

    public static String toString(User user) {
        String id = user.getID();
        String name = user.getName();
        Presences status = user.getPresence();
        return "User ["
            + "id='" + id
            + "', name='" + name
            + "', status='" + status
            + "']";
    }

    // forwarded events

    @EventListener
    public void newGameVersionAvailable(NewGameVersionAvailable event) {
        if (client != null && client.isReady()) {
            // don't broadcast update if it's too old (>10 mins)
            if (event.getInstant().isAfter(Instant.now().minusSeconds(60 * 10))) {
                String formatted = String.format("Preparing to update game servers to v%s", event.getVersion());
                broadcast(formatted);
            }
        }
    }

    public void gameUpdateStarted(GameUpdateStartedEvent event) {
        if (client != null && client.isReady()) {
            client.updatePresence(client.getOurUser().getPresence().equals(Presences.IDLE),
                Optional.of("Updating servers..."));
        }
    }

    @EventListener
    public void gameUpdateCompleted(GameUpdateCompletedEvent event) {
        if (client != null && client.isReady()) {
            if (event.getVersion() > 0) {
                broadcast(":ok_hand: TF2 game servers updated to v" + event.getVersion());
            }
            client.updatePresence(client.getOurUser().getPresence().equals(Presences.IDLE),
                Optional.empty());
        }
    }

    @EventListener
    public void gameUpdateFailed(GameUpdateFailedEvent event) {
        if (client != null && client.isReady()) {
            String list = event.getServers().stream()
                .map(s -> String.format("_%s_", s.getName()))
                .collect(Collectors.joining(", "));
            if (lastFailedBroadcast.isBefore(Instant.now().minus(12, ChronoUnit.HOURS))) {
                broadcast("TF2 servers pending update: " + list);
                lastFailedBroadcast = Instant.now();
            }
        }
    }
}
