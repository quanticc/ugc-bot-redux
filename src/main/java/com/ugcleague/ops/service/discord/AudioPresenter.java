package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.Command;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.audio.AudioPlayer;
import sx.blah.discord.util.audio.events.*;

import javax.annotation.PostConstruct;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static com.ugcleague.ops.util.DateUtil.formatDuration;
import static com.ugcleague.ops.util.DateUtil.formatHuman;

@Service
public class AudioPresenter implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AudioPresenter.class);

    private final CommandService commandService;
    private final DiscordService discordService;
    private final AudioStreamService audioStreamService;

    private OptionSpec<Integer> audioJoinSpec;
    private OptionSpec<String> audioEnqueueSpec;
    private Command audioCommand;
    private OptionSpec<Void> audioLeaveSpec;
    private OptionSpec<Integer> audioVolumeSpec;
    private OptionSpec<Void> audioSkipSpec;
    private OptionSpec<Void> audioPauseSpec;
    private OptionSpec<Void> audioResumeSpec;
    private OptionSpec<String> audioYoutubeSpec;
    private OptionSpec<Integer> audioRewindToSpec;
    private OptionSpec<Integer> audioForwardToSpec;
    private OptionSpec<Integer> audioRewindSpec;
    private OptionSpec<Integer> audioForwardSpec;
    private OptionSpec<Void> audioShuffleSpec;
    private OptionSpec<Void> audioLoopSpec;
    private OptionSpec<Void> audioStatusSpec;
    private OptionSpec<Void> audioClearSpec;

    @Autowired
    public AudioPresenter(CommandService commandService, DiscordService discordService,
                          AudioStreamService audioStreamService) {
        this.commandService = commandService;
        this.discordService = discordService;
        this.audioStreamService = audioStreamService;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
        initAudioCommand();
    }

    private void initAudioCommand() {
        OptionParser parser = newParser();
        audioJoinSpec = parser.accepts("join", "Joins a voice channel by order #")
            .withRequiredArg().ofType(Integer.class);
        audioLeaveSpec = parser.accepts("leave", "Leave the current voice channel");
        audioEnqueueSpec = parser.accepts("enqueue", "Puts an audio file to the end of the queue")
            .withRequiredArg().describedAs("filename");
        audioYoutubeSpec = parser.accepts("youtube", "Puts a youtube video to the end of the queue")
            .withRequiredArg().describedAs("watch-url");
        audioVolumeSpec = parser.accepts("volume", "Set volume % (0-100)")
            .withRequiredArg().ofType(Integer.class);
        audioSkipSpec = parser.accepts("skip", "Skip currently queued file");
        audioPauseSpec = parser.accepts("pause", "Pause playback");
        audioResumeSpec = parser.accepts("resume", "Resume playback");
        audioRewindToSpec = parser.accepts("rewindto", "Rewind playback to a given time in ms")
            .withRequiredArg().ofType(Integer.class);
        audioForwardToSpec = parser.accepts("forwardto", "Fast-forward playback to a given time in ms")
            .withRequiredArg().ofType(Integer.class);
        audioRewindSpec = parser.accepts("rewind", "Rewind playback by a given amount of time in ms")
            .withRequiredArg().ofType(Integer.class);
        audioForwardSpec = parser.accepts("forward", "Fast-forward playback by a given amount of time in ms")
            .withRequiredArg().ofType(Integer.class);
        audioShuffleSpec = parser.accepts("shuffle", "Shuffle the current playlist");
        audioLoopSpec = parser.accepts("loop", "Toggle looping the current track");
        audioStatusSpec = parser.accepts("status", "Display current player status");
        audioClearSpec = parser.accepts("clear", "Clears the current playlist");
        audioCommand = commandService.register(CommandBuilder.startsWith(".audio").support().originReplies()
            .description("Performs audio-related operations")
            .parser(parser).optionAliases(newAliasesMap(parser)).command(this::audio).build());
    }

    private String audio(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        if (message.getChannel().isPrivate()) {
            return "Must not be called from a private message channel";
        }

        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());

        if (optionSet.has(audioJoinSpec)) {
            Integer join = optionSet.valueOf(audioJoinSpec);
            if (message.getChannel().isPrivate()) {
                return "Can't be used in a private channel";
            }
            List<IVoiceChannel> voiceChannelList = message.getChannel().getGuild().getVoiceChannels();
            if (voiceChannelList.isEmpty()) {
                return "No voice channels in this server";
            }
            int index = Math.min(Math.max(0, join), voiceChannelList.size() - 1);
            IVoiceChannel voiceChannel = voiceChannelList.get(index);
            log.debug("Joining voice channel: {} ({})", voiceChannel.getName(), voiceChannel.getID());
            try {
                voiceChannel.join();
            } catch (MissingPermissionsException e) {
                return "Could not join voice channel";
            }
        }

        if (optionSet.has(audioLeaveSpec)) {
            Optional<IVoiceChannel> voiceChannel = message.getGuild().getVoiceChannels().stream()
                .filter(IVoiceChannel::isConnected).findAny();
            if (voiceChannel.isPresent()) {
                log.debug("Leaving voice channel: {} ({})", voiceChannel.get().getName(), voiceChannel.get().getID());
                voiceChannel.get().leave();
            } else {
                return "Not in a voice channel, use `.audio join #` to enter an audio channel";
            }
        }

        if (optionSet.has(audioEnqueueSpec)) {
            String enqueue = optionSet.valueOf(audioEnqueueSpec);
            File file = new File(enqueue);
            if (file.exists()) {
                try {
                    player.queue(file);
                    commandService.replyFrom(message, audioCommand, "Enqueued file: " + file);
                } catch (InterruptedException | DiscordException | MissingPermissionsException
                    | UnsupportedAudioFileException | IOException e) {
                    log.warn("Could not reply to user: {}", e.toString());
                }
            } else {
                try {
                    URL url = new URL(enqueue);
                    try {
                        player.queue(url);
                        commandService.replyFrom(message, audioCommand, "Enqueued URL: " + url);
                    } catch (InterruptedException | DiscordException | MissingPermissionsException
                        | UnsupportedAudioFileException | IOException e) {
                        log.warn("Could not reply to user: {}", e.toString());
                    }
                } catch (MalformedURLException e) {
                    // not a url
                    return "File does not exist or invalid URL";
                }
            }
        }

        if (optionSet.has(audioClearSpec)) {
            player.clean();
            player.inject();
        }

        if (optionSet.has(audioVolumeSpec)) {
            Integer volume = optionSet.valueOf(audioVolumeSpec);
            volume = Math.max(0, Math.min(100, volume));
            log.debug("Setting volume to {}% ({})", volume, volume / 100f);
            player.setVolume(volume / 100f);
        }

        if (optionSet.has(audioSkipSpec)) {
            log.debug("Skipping current track");
            player.skip();
        }

        if (optionSet.has(audioPauseSpec)) {
            log.debug("Pausing current track");
            player.setPaused(true);
        }

        if (optionSet.has(audioResumeSpec)) {
            log.debug("Resuming current track");
            player.setPaused(false);
        }

        if (optionSet.has(audioYoutubeSpec)) {
            audioStreamService.queueFromYouTube(player, optionSet.valueOf(audioYoutubeSpec));
        }

        if (optionSet.has(audioForwardSpec)) {
            Integer ms = Math.max(0, optionSet.valueOf(audioForwardSpec));
            String duration = formatHuman(Duration.ofMillis(ms), true);
            player.getCurrentTrack().fastForward(ms);
            return ":fast_forward: by " + duration;
        }

        if (optionSet.has(audioRewindSpec)) {
            Integer ms = Math.max(0, optionSet.valueOf(audioRewindSpec));
            String duration = formatHuman(Duration.ofMillis(ms), true);
            player.getCurrentTrack().rewind(ms);
            return ":rewind: by " + duration;
        }

        if (optionSet.has(audioForwardToSpec)) {
            Integer ms = Math.max(0, optionSet.valueOf(audioForwardToSpec));
            String duration = formatHuman(Duration.ofMillis(ms), true);
            player.getCurrentTrack().fastForwardTo(ms);
            return ":fast_forward: to " + duration;
        }

        if (optionSet.has(audioRewindToSpec)) {
            Integer ms = Math.max(0, optionSet.valueOf(audioRewindToSpec));
            String duration = formatHuman(Duration.ofMillis(ms), true);
            player.getCurrentTrack().rewindTo(ms);
            return ":rewind: to " + duration;
        }

        if (optionSet.has(audioLoopSpec)) {
            player.setLoop(!player.isLooping());
            return "Loop mode: " + (player.isLooping() ? "ON" : "OFF");
        }

        if (optionSet.has(audioShuffleSpec)) {
            player.shuffle();
            return getStatus(player);
        }

        if (optionSet.has(audioStatusSpec)) {
            return getStatus(player);
        }

        return "";
    }

    private String getStatus(AudioPlayer player) {
        AudioPlayer.Track track = player.getCurrentTrack();
        if (track != null) {
            String source = getSource(track);
            long total = track.getTotalTrackTime();
            int volume = (int) (player.getVolume() * 100);
            StringBuilder response = new StringBuilder();
            response.append("Status: ").append(player.isPaused() ? "**Paused**" : "**Playing**");
            if (player.isLooping()) {
                response.append(" [Loop ON]\n");
            } else {
                response.append("\n");
            }
            response.append(source).append(" ")
                .append(prettyDuration(total)).append("\n")
                .append("Volume: ").append(volume).append("\n")
                .append("**=== Playlist ===**\n").append(playlistToString(player));
            return response.toString();
        } else {
            return "Player is " + (player.isReady() ? "" : "NOT") + " ready.";
        }
    }

    private String prettyDuration(long millis) {
        if (millis >= 0) {
            return "[" + formatDuration(Duration.ofMillis(millis)) + "]";
        } else {
            return "";
        }
    }

    @EventSubscriber
    public void onPlayerInit(AudioPlayerInitEvent event) {
        // set the default volume - can be changed later
        log.debug("Audio player initialized");
        event.getPlayer().setVolume(0.3f);
    }

    private String getSource(AudioPlayer.Track track) {
        if (track == null) {
            return "";
        }
        Map<String, Object> metadata = track.getMetadata();
        if (metadata.containsKey("file")) {
            return ((File) metadata.get("file")).getName();
        } else if (metadata.containsKey("url")) {
            return metadata.get("url").toString();
        } else {
            return hex(track.hashCode());
        }
    }

    @EventSubscriber
    public void onVolumeChange(VolumeChangeEvent event) {
        log.debug("[Volume] {} -> {}", (int) (event.getOldValue() * 100), (int) (event.getNewValue() * 100));
    }

    @EventSubscriber
    public void onPause(PauseStateChangeEvent event) {
        if (event.getNewPauseState()) {
            log.debug("[Pausing]", getSource(event.getPlayer().getCurrentTrack()));
        } else {
            log.debug("[Resuming]", getSource(event.getPlayer().getCurrentTrack()));
        }
    }

    @EventSubscriber
    public void onShuffle(ShuffleEvent event) {
        log.debug("Shuffling {} tracks. Current playlist: {}", event.getPlayer().playlistSize(),
            playlistToString(event.getPlayer()));
    }

    @EventSubscriber
    public void onLoop(LoopStateChangeEvent event) {
        if (event.getNewLoopState()) {
            log.debug("[Loop Enabled]", getSource(event.getPlayer().getCurrentTrack()));
        } else {
            log.debug("[Loop Disabled]", getSource(event.getPlayer().getCurrentTrack()));
        }
    }

    private String hex(int number) {
        return Integer.toHexString(number);
    }

    private String playlistToString(AudioPlayer player) {
        return player.getPlaylist().stream().map(this::getSource).collect(Collectors.joining(", "));
    }
}
