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
import sx.blah.discord.api.DiscordException;
import sx.blah.discord.api.MissingPermissionsException;
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.impl.events.AudioPlayEvent;
import sx.blah.discord.handle.impl.events.AudioQueuedEvent;
import sx.blah.discord.handle.impl.events.AudioStopEvent;
import sx.blah.discord.handle.impl.events.AudioUnqueuedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.AudioChannel;

import javax.annotation.PostConstruct;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ugcleague.ops.service.discord.CommandService.newParser;
import static java.util.Arrays.asList;

@Service
public class AudioPresenter implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AudioPresenter.class);

    private final CommandService commandService;
    private final DiscordService discordService;

    private OptionSpec<Integer> audioJoinSpec;
    private OptionSpec<String> audioEnqueueSpec;
    private Command audioCommand;
    private OptionSpec<Void> audioLeaveSpec;
    private OptionSpec<Integer> audioUnqueueSpec;
    private OptionSpec<Void> audioListSpec;
    private OptionSpec<Integer> audioVolumeSpec;
    private OptionSpec<Void> audioSkipSpec;
    private OptionSpec<Void> audioPauseSpec;
    private OptionSpec<Void> audioResumeSpec;

    @Autowired
    public AudioPresenter(CommandService commandService, DiscordService discordService) {
        this.commandService = commandService;
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
        initAudioCommand();
    }

    private void initAudioCommand() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("join", "-j");
        aliases.put("enqueue", "-e");
        aliases.put("unqueue", "-u");
        aliases.put("leave", "-l");
//        aliases.put("list", "-L");
        aliases.put("volume", "-v");
        aliases.put("skip", "-s");
        aliases.put("pause", "-p");
        aliases.put("resume", "-r");
        aliases.put("?", "-?");
        OptionParser parser = newParser();
        audioJoinSpec = parser.acceptsAll(asList("j", "join"), "Joins a voice channel by order #")
            .withRequiredArg().ofType(Integer.class);
        audioLeaveSpec = parser.acceptsAll(asList("l", "leave"), "Leave the current voice channel");
        audioEnqueueSpec = parser.acceptsAll(asList("e", "enqueue"), "Puts an audio file to the end of the queue")
            .withRequiredArg().describedAs("filename");
        audioUnqueueSpec = parser.acceptsAll(asList("u", "unqueue"), "Removes file from the queue by index")
            .withRequiredArg().ofType(Integer.class);
//        audioListSpec = parser.acceptsAll(asList("L", "list"), "Display list of queued files");
        audioVolumeSpec = parser.acceptsAll(asList("v", "volume"), "Set volume % (0-100)")
            .withRequiredArg().ofType(Integer.class);
        audioSkipSpec = parser.acceptsAll(asList("s", "skip"), "Skip currently queued file");
        audioPauseSpec = parser.acceptsAll(asList("p", "pause"), "Pause playing");
        audioResumeSpec = parser.acceptsAll(asList("r", "resume"), "Resume playing");
        audioCommand = commandService.register(CommandBuilder.startsWith(".audio").support().originReplies()
            .description("Performs audio-related operations")
            .parser(parser).optionAliases(aliases).command(this::audio).build());
    }

    private String audio(IMessage message, OptionSet optionSet) {
        if (optionSet.has("?")) {
            return null;
        }

        AudioChannel audioChannel = discordService.getClient().getAudioChannel();

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
            voiceChannel.join();
        }

        if (optionSet.has(audioLeaveSpec)) {
            Optional<IVoiceChannel> voiceChannel = discordService.getClient().getConnectedVoiceChannel();
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
                audioChannel.queueFile(file);
                try {
                    commandService.replyFrom(message, audioCommand, "Enqueued file: " + file);
                } catch (InterruptedException | DiscordException | MissingPermissionsException e) {
                    log.warn("Could not reply to user: {}", e.toString());
                }
            } else {
                try {
                    URL url = new URL(enqueue);
                    audioChannel.queueUrl(url);
                    try {
                        commandService.replyFrom(message, audioCommand, "Enqueued URL: " + url);
                    } catch (InterruptedException | DiscordException | MissingPermissionsException e) {
                        log.warn("Could not reply to user: {}", e.toString());
                    }
                } catch (MalformedURLException e) {
                    // not a url
                    return "File does not exist or invalid URL";
                }
            }
        }

        if (optionSet.has(audioUnqueueSpec)) {
            Integer unqueue = optionSet.valueOf(audioUnqueueSpec);
            unqueue = Math.max(0, Math.min(audioChannel.getQueueSize() - 1, unqueue));
            log.debug("Unqueueing by index: {}", unqueue);
            audioChannel.unqueue(unqueue);
        }

//        if (optionSet.has(audioListSpec)) {
//
//        }

        if (optionSet.has(audioVolumeSpec)) {
            Integer volume = optionSet.valueOf(audioVolumeSpec);
            volume = Math.max(0, Math.min(100, volume));
            log.debug("Setting volume to {}% ({})", volume, volume / 100f);
            audioChannel.setVolume(volume / 100f);
        }

        if (optionSet.has(audioSkipSpec)) {
            log.debug("Skipping current track");
            audioChannel.skip();
        }

        if (optionSet.has(audioPauseSpec)) {
            log.debug("Pausing current track");
            audioChannel.pause();
        }

        if (optionSet.has(audioResumeSpec)) {
            log.debug("Resuming current track");
            audioChannel.resume();
        }

        return "";
    }

    @EventSubscriber
    public void onAudioPlayed(AudioPlayEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Playing] {} with metadata: {}}", source, event.getFormat().toString());
    }

    @EventSubscriber
    public void onAudioStopped(AudioStopEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Stopping] {} with metadata: {}}", source, event.getFormat().toString());
    }

    @EventSubscriber
    public void onAudioEnqueued(AudioQueuedEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Enqueued] {} with metadata: {}}", source, event.getFormat().toString());
    }

    @EventSubscriber
    public void onAudioUnqueued(AudioUnqueuedEvent event) {
        String source = event.getFileSource().map(File::toString)
            .orElseGet(() -> event.getUrlSource().map(URL::toString).orElse(""));
        AudioInputStream stream = event.getStream();
        log.debug("[Unqueued] {} with metadata: {}}", source, event.getFormat().toString());
    }
}
