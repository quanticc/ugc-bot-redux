package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.SoundBite;
import com.ugcleague.ops.repository.mongo.SoundBiteRepository;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import com.ugcleague.ops.service.discord.util.SilenceProvider;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.audio.AudioPlayer;
import sx.blah.discord.util.audio.events.SkipEvent;
import sx.blah.discord.util.audio.events.TrackFinishEvent;
import sx.blah.discord.util.audio.events.TrackQueueEvent;
import sx.blah.discord.util.audio.events.TrackStartEvent;

import javax.annotation.PostConstruct;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;

@Service
@Transactional
public class SoundBitePresenter implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SoundBitePresenter.class);
    private static final Pattern YOUTUBE_URL = Pattern.compile("(?:https?://)?(?:(?:(?:www\\.?)?youtube\\.com(?:/(?:(?:watch\\?.*?(v=[^&\\s]+).*)|(?:v(/.*))|(channel/.+)|(?:user/(.+))|(?:results\\?(search_query=.+))))?)|(?:youtu\\.be(/.*)?))");

    private final DiscordService discordService;
    private final SoundBiteRepository soundBiteRepository;
    private final SettingsService settingsService;
    private final CommandService commandService;
    private final Executor taskExecutor;
    private final AudioStreamService audioStreamService;

    private final Object lock = new Object();
    private final Map<String, Integer> volumeMap = new ConcurrentHashMap<>();

    private OptionSpec<Void> soundbitesEnableSpec;
    private OptionSpec<Void> soundbitesDisableSpec;
    private OptionSpec<String> soundbitesNonOptionSpec;
    private OptionSpec<String> soundbitesRemoveSpec;
    private OptionSpec<Void> soundbitesListSpec;
    private OptionSpec<String> soundbitesRandomSpec;
    private OptionSpec<Void> soundbitesPoolSpec;
    private OptionSpec<Void> soundbitesSeriesSpec;
    private OptionSpec<String> soundbitesInfoSpec;
    private OptionSpec<Void> soundbitesFolderSpec;
    private OptionSpec<Integer> volumeNonOptionSpec;
    private OptionSpec<Integer> soundbitesVolumeSpec;
    private OptionSpec<String> soundbitesEditSpec;
    private OptionSpec<Void> soundbitesBlacklistAddSpec;
    private OptionSpec<Void> soundbitesBlacklistRemoveSpec;
    private OptionSpec<String> queueNonOptionSpec;

    @Autowired
    public SoundBitePresenter(DiscordService discordService, SoundBiteRepository soundBiteRepository,
                              SettingsService settingsService, CommandService commandService, Executor taskExecutor,
                              AudioStreamService audioStreamService) {
        this.discordService = discordService;
        this.soundBiteRepository = soundBiteRepository;
        this.settingsService = settingsService;
        this.commandService = commandService;
        this.taskExecutor = taskExecutor;
        this.audioStreamService = audioStreamService;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
        commandService.register(CommandBuilder.equalsTo(".soundstats").unrestricted().originReplies()
            .description("Sound playback statistics").noParser().command((message, optionSet) -> {
                if (!message.getChannel().isPrivate()
                    && settingsService.getSettings().getSoundBitesWhitelist().contains(message.getGuild().getID())) {
                    List<PlayCount> counts = settingsService.getSettings().getPlayCount().entrySet().stream()
                        .map(e -> new PlayCount(e.getValue(), e.getKey())).collect(Collectors.toList());
                    int sum = counts.stream().map(p -> p.count).reduce(0, Integer::sum);
                    return "`" + sum + "` total plays of `" + counts.size() + "` unique sounds\n**Top 25**\n" +
                        counts.stream().filter(p -> p.count > 1)
                            .sorted().limit(25)
                            .map(p -> "`" + p.count + "` " + p.name)
                            .collect(Collectors.joining("\n"));
                } else {
                    return "";
                }
            }).build());
        commandService.register(CommandBuilder.equalsTo(".skip").unrestricted().originReplies()
            .description("Skip current audio").noParser().command((message, optionSet) -> {
                if (!message.getChannel().isPrivate()
                    && settingsService.getSettings().getSoundBitesWhitelist().contains(message.getGuild().getID())) {
                    AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
                    player.skip();
                }
                return "";
            }).build());
        configureSoundbitesCommand();
        configureQueueCommand();
        configureVolumeCommand();
    }

    private void configureSoundbitesCommand() {
        OptionParser parser = newParser();
        soundbitesEnableSpec = parser.accepts("enable", "Enable soundbites in this guild");
        soundbitesDisableSpec = parser.accepts("disable", "Disable soundbites in this guild");
        soundbitesBlacklistAddSpec = parser.accepts("blacklist-add", "Blacklist soundbites in this channel");
        soundbitesBlacklistRemoveSpec = parser.accepts("blacklist-remove", "Remove soundbite blacklist in this channel");
        soundbitesRemoveSpec = parser.accepts("remove", "Remove a soundbite")
            .withRequiredArg().describedAs("alias");
        soundbitesEditSpec = parser.accepts("update", "Update a soundbite")
            .withRequiredArg().describedAs("alias");
        soundbitesInfoSpec = parser.accepts("info", "Get info of a soundbite")
            .withRequiredArg().describedAs("alias");
        soundbitesListSpec = parser.accepts("list", "List all soundbites");
        soundbitesRandomSpec = parser.accepts("random", "Define a folder as the pool of random sounds")
            .withRequiredArg().describedAs("folder");
        soundbitesPoolSpec = parser.accepts("pool", "Define a group of sounds and pick a random one to play");
        soundbitesSeriesSpec = parser.accepts("series", "Define a group of sounds to play in series");
        soundbitesFolderSpec = parser.accepts("folder", "Define a folder of sounds and pick a random one to play");
        soundbitesVolumeSpec = parser.accepts("volume", "Set the volume to use when playing this sound")
            .withRequiredArg().ofType(Integer.class).defaultsTo(100);
        soundbitesNonOptionSpec = parser.nonOptions("A series of aliases of a given audio path (last argument)");
        commandService.register(CommandBuilder.startsWith(".sounds").master()
            .description("Manage soundbite settings")
            .command(this::soundbites).parser(parser).optionAliases(newAliasesMap(parser)).originReplies().build());
    }

    private void configureQueueCommand() {
        OptionParser parser = newParser();
        queueNonOptionSpec = parser.nonOptions("YouTube Video URL to enqueue").ofType(String.class);
        // TODO: add volume control, currently can't tie d4j audio events to a YT track
//        queueVolumeSpec = parser.accepts("volume", "Play with a given volume")
//            .withRequiredArg().ofType(Integer.class).defaultsTo(20);
        commandService.register(CommandBuilder.startsWith(".queue").unrestricted().originReplies()
            .description("Queue a YouTube video to the bot").parser(parser).command((message, optionSet) -> {
                if (message.getChannel().isPrivate()) {
                    return "Join a voice channel and don't use private messages";
                }
                List<String> urls = optionSet.valuesOf(queueNonOptionSpec);
                if (urls.size() == 0) {
                    return "You have to enter a YouTube URL";
                }
                AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
                String queued = urls.stream().map(this::extractVideoId)
                    .filter(Optional::isPresent)
                    .map(id -> queueYouTube(player, id.get()))
                    .filter(s -> s != null)
                    .collect(Collectors.joining(", "));
                deleteMessage(message, 1, TimeUnit.SECONDS);
                if (queued != null && !queued.isEmpty()) {
                    IUser user = message.getAuthor();
                    return user.getName() + "#" + user.getDiscriminator() + " added to queue: " + queued;
                } else {
                    return "Nothing to queue";
                }
            }).build());
    }

    private String queueYouTube(AudioPlayer player, String id) {
        if (audioStreamService.queueFromYouTube(player, id)) {
            return id;
        } else {
            return null;
        }
    }

    public Optional<String> extractVideoId(String url) {
        Matcher matcher = YOUTUBE_URL.matcher(url);
        if (matcher.find()) {
            String group1 = matcher.group(1);
            String group2 = matcher.group(2);
            String group6 = matcher.group(6);
            if (group1 != null && !group1.isEmpty()) {
                return Optional.of(group1.substring(2)); // strip "v="
            } else if (group2 != null && !group2.isEmpty() && !group2.substring(1).isEmpty()) {
                return Optional.of(group2.substring(1)); // strip "/"
            } else if (group6 != null && !group6.isEmpty() && !group6.substring(1).isEmpty()) {
                return Optional.of(group6.substring(1)); // strip "/"
            }
        }
        return Optional.empty();
    }

    private void configureVolumeCommand() {
        OptionParser parser = newParser();
        volumeNonOptionSpec = parser.nonOptions("0-100 volume percentage").ofType(Integer.class);
        commandService.register(CommandBuilder.startsWith(".volume").unrestricted().originReplies()
            .description("Set volume % (0-100)").parser(parser).command((message, optionSet) -> {
                if (message.getChannel().isPrivate()) {
                    return "";
                }
                List<Integer> values = optionSet.valuesOf(volumeNonOptionSpec);
                AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
                if (values.size() == 0) {
                    return "Enter a 0-100 value.";
                } else if (values.size() == 1) {
                    int volume = values.get(0);
                    volume = Math.max(0, Math.min(100, volume));
                    log.debug("Setting volume to {}% ({})", volume, volume / 100f);
                    player.setVolume(volume / 100f);
                } else if (values.size() == 2) {
                    // sliding volume in 5 seconds
                    slideVolume(player, values.get(0), values.get(1), 5);
                } else {
                    slideVolume(player, values.get(0), values.get(1), Math.max(1, values.get(2)));
                }
                return ":ok_hand:";
            }).build());
    }

    private void slideVolume(AudioPlayer player, int start, int end, int seconds) {
        // use 100ms resolution
        CompletableFuture.runAsync(() -> {
            int volume = Math.max(0, Math.min(100, start));
            float step = (float) (Math.max(0, Math.min(100, end)) - volume) / (seconds * 10);
            try {
                log.debug("Sliding volume from {} to {} in {} seconds", start, end, seconds);
                for (int i = 0; i <= seconds * 10; i++) {
                    player.setVolume((volume + step * i) / 100f);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted volume sliding");
                player.setVolume(Math.max(0, Math.min(100, end)) / 100f);
            }
        }, taskExecutor);
    }

    private static class PlayCount implements Comparable<PlayCount> {
        private final int count;
        private final String name;

        private PlayCount(int count, String name) {
            this.count = count;
            this.name = name;
        }


        @Override
        public int compareTo(PlayCount o) {
            return -Integer.compare(count, o.count);
        }
    }

    private String soundbites(IMessage message, OptionSet optionSet) {
        List<String> nonOptions = optionSet.valuesOf(soundbitesNonOptionSpec);
        int volume = Math.min(100, Math.max(0, optionSet.valueOf(soundbitesVolumeSpec)));
        if (optionSet.has(soundbitesEnableSpec)) {
            if (message.getChannel().isPrivate()) {
                return "Does not work with private channels yet";
            }
            // .sounds enable
            settingsService.getSettings().getSoundBitesWhitelist().add(message.getChannel().getGuild().getID());
        } else if (optionSet.has(soundbitesDisableSpec)) {
            if (message.getChannel().isPrivate()) {
                return "Does not work with private channels yet";
            }
            // .sounds disable
            settingsService.getSettings().getSoundBitesWhitelist().remove(message.getChannel().getGuild().getID());
        } else if (optionSet.has(soundbitesRemoveSpec)) {
            String key = optionSet.valueOf(soundbitesRemoveSpec);
            if (!soundBiteRepository.exists(key)) {
                return "No sound found with that alias!";
            }
            soundBiteRepository.delete(key);
        } else if (optionSet.has(soundbitesInfoSpec)) {
            String key = optionSet.valueOf(soundbitesInfoSpec);
            if (!soundBiteRepository.exists(key)) {
                return "No sound found with that alias!";
            } else {
                SoundBite bite = soundBiteRepository.findOne(key);
                SoundBite.PlaybackMode mode = bite.getMode();
                switch (mode) {
                    case FOLDER:
                        return "Key: " + bite.getId() + "\nFolder: " + bite.getPath() + "\nVolume: " + bite.getVolume();
                    case SERIES:
                        return "Key: " + bite.getId() + "\nPlaylist: " + bite.getPaths() + "\nVolume: " + bite.getVolume();
                    case POOL:
                        return "Key: " + bite.getId() + "\nPool: " + bite.getPaths() + "\nVolume: " + bite.getVolume();
                    case SINGLE:
                    default:
                        return "Key: " + bite.getId() + "\nPath: " + bite.getPath() + "\nVolume: " + bite.getVolume();
                }
            }
        } else if (optionSet.has(soundbitesEditSpec)) {
            String key = optionSet.valueOf(soundbitesEditSpec);
            if (!soundBiteRepository.exists(key)) {
                return "No sound found with that alias!";
            } else {
                // TODO add other cases - needs refactor
                SoundBite bite = soundBiteRepository.findOne(key);
                if (optionSet.has(soundbitesVolumeSpec)) {
                    bite.setVolume(volume);
                }
                soundBiteRepository.save(bite);
            }
        } else if (optionSet.has(soundbitesListSpec)) {
            return soundBiteRepository.findAll().stream().map(SoundBite::getId).collect(Collectors.joining(", "));
        } else if (optionSet.has(soundbitesRandomSpec)) {
            String dir = optionSet.valueOf(soundbitesRandomSpec);
            Path path = Paths.get(dir);
            if (Files.exists(path) && Files.isDirectory(path)) {
                settingsService.getSettings().setRandomSoundDir(path.toString());
            } else {
                return "Invalid directory!";
            }
        } else if (nonOptions.size() > 1) {
            // .sounds <key> <paths...>
            String key = nonOptions.get(0);
            List<String> paths = nonOptions.subList(1, nonOptions.size());
            if (paths.size() == 1) {
                SoundBite bite = new SoundBite();
                bite.setId(key.toLowerCase());
                bite.setPath(paths.get(0));
                bite.setVolume(volume);
                File file = new File(bite.getPath());
                if (optionSet.has(soundbitesFolderSpec)) {
                    bite.setMode(SoundBite.PlaybackMode.FOLDER);
                    if (!file.exists() || !file.isDirectory()) {
                        return "Given path must be a directory";
                    }
                } else {
                    if (!file.exists()) {
                        return "Given path must exist";
                    }
                    bite.setMode(SoundBite.PlaybackMode.SINGLE);
                }
                soundBiteRepository.save(bite);
            } else if (optionSet.has(soundbitesSeriesSpec)) {
                SoundBite bite = new SoundBite();
                bite.setId(key.toLowerCase());
                bite.setPaths(paths);
                bite.setMode(SoundBite.PlaybackMode.SERIES);
                bite.setVolume(volume);
                soundBiteRepository.save(bite);
            } else if (optionSet.has(soundbitesPoolSpec)) {
                SoundBite bite = new SoundBite();
                bite.setId(key.toLowerCase());
                bite.setPaths(paths);
                bite.setMode(SoundBite.PlaybackMode.POOL);
                bite.setVolume(volume);
                soundBiteRepository.save(bite);
            } else {
                // treat as multiple aliases to a single sound
                // .sounds <alias...> <path>
                String path = nonOptions.get(nonOptions.size() - 1);
                List<String> aliases = nonOptions.subList(0, nonOptions.size() - 1);
                boolean folderMode = optionSet.has(soundbitesFolderSpec);
                List<SoundBite> soundBites = aliases.stream().map(s -> newSoundBite(s, path, folderMode, volume)).collect(Collectors.toList());
                soundBiteRepository.save(soundBites);
            }
        } else if (optionSet.has(soundbitesBlacklistAddSpec)) {
            if (message.getChannel().isPrivate()) {
                return "Does not work with private channels yet";
            }
            settingsService.getSettings().getSoundBitesBlacklist().add(message.getChannel().getID());
        } else if (optionSet.has(soundbitesBlacklistRemoveSpec)) {
            if (message.getChannel().isPrivate()) {
                return "Does not work with private channels yet";
            }
            settingsService.getSettings().getSoundBitesBlacklist().remove(message.getChannel().getID());
        } else {
            return null;
        }
        return ":ok_hand:";
    }

    private SoundBite newSoundBite(String alias, String path, boolean folderMode, int volume) {
        SoundBite bite = new SoundBite();
        bite.setId(alias.toLowerCase());
        bite.setPath(path);
        bite.setVolume(volume);
        if (folderMode) {
            bite.setMode(SoundBite.PlaybackMode.FOLDER);
        } else {
            bite.setMode(SoundBite.PlaybackMode.SINGLE);
        }
        return bite;
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        CompletableFuture.runAsync(() -> asyncOnMessage(e), taskExecutor);
    }

    public void asyncOnMessage(MessageReceivedEvent e) {
        IMessage message = e.getMessage();
        if (!message.getChannel().isPrivate()
            && settingsService.getSettings().getSoundBitesWhitelist().contains(message.getGuild().getID())
            && !settingsService.getSettings().getSoundBitesBlacklist().contains(message.getChannel().getID())) {
            if (message.getContent().toLowerCase().equals("!w")) {
                playFromDir(settingsService.getSettings().getRandomSoundDir(), message, true, null);
            } else {
                String[] parts = message.getContent().toLowerCase().split("\\-", 32);
                for (int i = 0; i < parts.length; i++) {
                    String key = parts[i].trim();
                    Optional<SoundBite> soundBite = soundBiteRepository.findById(key);
                    if (soundBite.isPresent()) {
                        SoundBite bite = soundBite.get();
                        SoundBite.PlaybackMode mode = bite.getMode();
                        // TODO refactor
                        if (mode == SoundBite.PlaybackMode.POOL) {
                            File source = new File(bite.getPaths().get(RandomUtils.nextInt(0, bite.getPaths().size())));
                            if (!source.exists()) {
                                log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                            }
                            play(source, message, bite.getVolume());
                        } else if (mode == SoundBite.PlaybackMode.SERIES) {
                            bite.getPaths().stream().map(File::new).filter(File::exists)
                                .forEach(f -> play(f, message, bite.getVolume()));
                        } else if (mode == SoundBite.PlaybackMode.FOLDER) {
                            File source = new File(bite.getPath());
                            if (!source.exists() || !source.isDirectory()) {
                                log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                            }
                            playFromDir(source.toString(), message, false, bite.getVolume());
                        } else {
                            File source = new File(bite.getPath());
                            if (!source.exists()) {
                                log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                            }
                            play(source, message, bite.getVolume());
                        }
                    } else if (key.matches("^s[0-9]+$")) {
                        String value = key.substring(1);
                        try {
                            long millis = Long.parseLong(value);
                            queueSilence(message, Math.max(20, Math.min(5000L, millis)));
                        } catch (NumberFormatException ex) {
                            log.warn("Invalid numeric value", ex);
                        }
                    } else if (i == 0) {
                        // abort if the first sound is not a valid bite (probable false positive)
                        break;
                    }
                }
            }
        }
    }

    private void playFromDir(String dirStr, IMessage message, boolean delete, Integer volume) {
        if (dirStr != null) {
            Path dir = Paths.get(dirStr);
            try {
                List<Path> list = Files.list(dir).filter(p -> !Files.isDirectory(p))
                    .collect(Collectors.toList());
                play(list.get(RandomUtils.nextInt(0, list.size())).toFile(), message, volume);
                if (delete) {
                    deleteMessage(message, 1, TimeUnit.SECONDS);
                }
            } catch (IOException e) {
                log.warn("Could not create list of random sounds", e);
            }
        }
    }

    private void deleteMessage(IMessage message, long timeout, TimeUnit unit) {
        CompletableFuture.runAsync(() -> {
            try {
                unit.sleep(timeout);
                discordService.deleteMessage(message);
            } catch (DiscordException | MissingPermissionsException | InterruptedException ex) {
                log.warn("Could not perform cleanup: {}", ex.toString());
            }
        }, taskExecutor);
    }

    private void play(File source, IMessage message, Integer volume) {
        try {
            Optional<IVoiceChannel> voiceChannel = message.getAuthor().getConnectedVoiceChannels()
                .stream().filter(v -> message.getGuild().equals(v.getGuild()))
                .findAny();
            if (voiceChannel.isPresent()) {
                synchronized (lock) {
                    if (tryJoin(voiceChannel)) {
                        AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
                        volumeMap.put(source.getName(), volume != null ? volume : 100);
                        player.queue(source);
                    }
                }
            }
        } catch (UnsupportedAudioFileException | IOException e) {
            log.warn("Unable to play sound bite", e);
        }
    }

    private void queueSilence(IMessage message, long length) {
        Optional<IVoiceChannel> voiceChannel = message.getAuthor().getConnectedVoiceChannels()
            .stream().filter(v -> message.getGuild().equals(v.getGuild()))
            .findAny();
        if (voiceChannel.isPresent()) {
            synchronized (lock) {
                if (tryJoin(voiceChannel)) {
                    AudioPlayer player = AudioPlayer.getAudioPlayerForGuild(message.getGuild());
                    log.debug("Queueing silence for {} ms", length);
                    player.queue(new SilenceProvider(length));
                }
            }
        }
    }

    private boolean tryJoin(Optional<IVoiceChannel> voiceChannel) {
        if (!voiceChannel.get().isConnected()) {
            try {
                voiceChannel.get().join();
                return true;
            } catch (MissingPermissionsException e) {
                log.warn("Could not join voice channel, missing permissions");
                return false;
            }
        } else {
            return true;
        }
    }

    private String getSource(AudioPlayer.Track track) {
        if (track == null) {
            return "";
        }
        Map<String, Object> metadata = track.getMetadata();
        if (metadata == null) {
            return Integer.toHexString(track.hashCode());
        } else if (metadata.containsKey("file")) {
            return ((File) metadata.get("file")).getName();
        } else if (metadata.containsKey("url")) {
            return metadata.get("url").toString();
        } else {
            return Integer.toHexString(track.hashCode());
        }
    }

    @EventSubscriber
    public void onTrackStart(TrackStartEvent event) {
        log.debug("[Started] {}", getSource(event.getTrack()));
        Map<String, Object> metadata = event.getTrack().getMetadata();
        if (metadata != null && metadata.containsKey("file")) {
            File source = ((File) metadata.get("file"));
            Integer count = settingsService.getSettings().getPlayCount().getOrDefault(source.getName(), 0);
            settingsService.getSettings().getPlayCount().put(source.getName(), count + 1);
            event.getPlayer().setVolume(volumeMap.getOrDefault(source.getName(), 30) / 100f);
        }
    }

    @EventSubscriber
    public void onTrackEnqueue(TrackQueueEvent event) {
        log.debug("[Enqueued] {}", getSource(event.getTrack()));
    }

    @EventSubscriber
    public void onTrackFinish(TrackFinishEvent event) {
        log.debug("[Finished] {}", getSource(event.getOldTrack()));
    }

    @EventSubscriber
    public void onSkip(SkipEvent event) {
        log.debug("[Skipped] {}", getSource(event.getTrack()));
    }

}
