package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.SoundBite;
import com.ugcleague.ops.repository.mongo.SoundBiteRepository;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.handle.AudioChannel;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ugcleague.ops.service.discord.CommandService.newAliasesMap;
import static com.ugcleague.ops.service.discord.CommandService.newParser;

@Service
@Transactional
public class SoundBitePresenter implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SoundBitePresenter.class);

    private final DiscordService discordService;
    private final SoundBiteRepository soundBiteRepository;
    private final SettingsService settingsService;
    private final CommandService commandService;
    private final Executor taskExecutor;

    //private final Map<File, IVoiceChannel> playing = new ConcurrentHashMap<>();
    //private final Map<IVoiceChannel, AtomicInteger> queueCounter = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    private OptionSpec<Void> soundbitesEnableSpec;
    private OptionSpec<Void> soundbitesDisableSpec;
    private OptionSpec<String> soundbitesNonOptionSpec;
    private OptionSpec<String> soundbitesRemoveSpec;
    private OptionSpec<Void> soundbitesListSpec;
    private OptionSpec<String> soundbitesRandomSpec;
    private OptionSpec<String> soundbitesAnswerSpec;
    private OptionSpec<Void> soundbitesPoolSpec;
    private OptionSpec<Void> soundbitesSeriesSpec;
    private OptionSpec<String> soundbitesInfoSpec;
    private OptionSpec<Void> soundbitesFolderSpec;

    @Autowired
    public SoundBitePresenter(DiscordService discordService, SoundBiteRepository soundBiteRepository,
                              SettingsService settingsService, CommandService commandService, Executor taskExecutor) {
        this.discordService = discordService;
        this.soundBiteRepository = soundBiteRepository;
        this.settingsService = settingsService;
        this.commandService = commandService;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
        OptionParser parser = newParser();
        soundbitesEnableSpec = parser.accepts("enable", "Enable soundbites in this guild");
        soundbitesDisableSpec = parser.accepts("disable", "Disable soundbites in this guild");
        soundbitesRemoveSpec = parser.accepts("remove", "Remove a soundbite")
            .withRequiredArg().describedAs("alias");
        soundbitesInfoSpec = parser.accepts("info", "Get info of a soundbite")
            .withRequiredArg().describedAs("alias");
        soundbitesListSpec = parser.accepts("list", "List all soundbites");
        soundbitesRandomSpec = parser.accepts("random", "Define a folder as the pool of random sounds")
            .withRequiredArg().describedAs("folder");
        soundbitesAnswerSpec = parser.accepts("answer", "Define a folder as the pool of 8ball sounds")
            .withRequiredArg().describedAs("folder");
        soundbitesPoolSpec = parser.accepts("pool", "Define a group of sounds and pick a random one to play");
        soundbitesSeriesSpec = parser.accepts("series", "Define a group of sounds to play in series");
        soundbitesFolderSpec = parser.accepts("folder", "Define a folder of sounds and pick a random one to play");
        soundbitesNonOptionSpec = parser.nonOptions("A series of aliases of a given audio path (last argument)");
        Map<String, String> aliases = newAliasesMap();
        aliases.put("enable", "--enable");
        aliases.put("disable", "--disable");
        aliases.put("remove", "--remove");
        aliases.put("info", "--info");
        aliases.put("random", "--random");
        aliases.put("answer", "--answer");
        aliases.put("list", "--list");
        aliases.put("pool", "--pool");
        aliases.put("series", "--series");
        commandService.register(CommandBuilder.startsWith(".sounds").master()
            .description("Manage soundbite settings")
            .command(this::soundbites).parser(parser).optionAliases(aliases).originReplies().build());
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
                    case SERIES:
                        return "Key: " + bite.getId() + "\nPlaylist: " + bite.getPaths();
                    case POOL:
                        return "Key: " + bite.getId() + "\nPool: " + bite.getPaths();
                    case SINGLE:
                    default:
                        return "Key: " + bite.getId() + "\nPath: " + bite.getPath();
                }
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
        } else if (optionSet.has(soundbitesAnswerSpec)) {
            String dir = optionSet.valueOf(soundbitesAnswerSpec);
            Path path = Paths.get(dir);
            if (Files.exists(path) && Files.isDirectory(path)) {
                settingsService.getSettings().setAnswerSoundDir(path.toString());
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
                soundBiteRepository.save(bite);
            } else if (optionSet.has(soundbitesPoolSpec)) {
                SoundBite bite = new SoundBite();
                bite.setId(key.toLowerCase());
                bite.setPaths(paths);
                bite.setMode(SoundBite.PlaybackMode.POOL);
                soundBiteRepository.save(bite);
            } else {
                // treat as multiple aliases to a single sound
                // .sounds <alias...> <path>
                String path = nonOptions.get(nonOptions.size() - 1);
                List<String> aliases = nonOptions.subList(0, nonOptions.size() - 1);
                List<SoundBite> soundBites = aliases.stream().map(s -> newSoundBite(s, path)).collect(Collectors.toList());
                soundBiteRepository.save(soundBites);
            }
        } else {
            return null;
        }
        return ":ok_hand:";
    }

    private SoundBite newSoundBite(String alias, String path) {
        SoundBite bite = new SoundBite();
        bite.setId(alias.toLowerCase());
        bite.setPath(path);
        return bite;
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        IMessage message = e.getMessage();
        if (!message.getChannel().isPrivate()
            && settingsService.getSettings().getSoundBitesWhitelist().contains(message.getGuild().getID())) {
            if (message.getContent().toLowerCase().equals("!w")) {
                playFromDir(settingsService.getSettings().getRandomSoundDir(), message, true);
            } else if (message.getContent().endsWith("?") || message.getContent().toLowerCase().equals("!")) {
                playFromDir(settingsService.getSettings().getAnswerSoundDir(), message, false);
            } else {
                Optional<SoundBite> soundBite = soundBiteRepository.findById(message.getContent().toLowerCase());
                if (soundBite.isPresent()) {
                    SoundBite bite = soundBite.get();
                    SoundBite.PlaybackMode mode = bite.getMode();
                    File source;
                    switch (mode) {
                        case POOL:
                            source = new File(bite.getPaths().get(RandomUtils.nextInt(0, bite.getPaths().size())));
                            if (!source.exists()) {
                                log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                            }
                            play(source, message);
                            break;
                        case SERIES:
                            bite.getPaths().stream().map(File::new).filter(File::exists)
                                .forEach(f -> play(f, message));
                            break;
                        case FOLDER:
                            source = new File(bite.getPath());
                            if (!source.exists() || !source.isDirectory()) {
                                log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                            }
                            playFromDir(source.toString(), message, true);
                        default:
                        case SINGLE:
                            source = new File(bite.getPath());
                            if (!source.exists()) {
                                log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                            }
                            play(source, message);
                            break;
                    }
                }
            }
        }
    }

    private void playFromDir(String dirStr, IMessage message, boolean delete) {
        if (dirStr != null) {
            Path dir = Paths.get(dirStr);
            try {
                List<Path> list = Files.list(dir).filter(p -> !Files.isDirectory(p))
                    .collect(Collectors.toList());
                CompletableFuture.runAsync(() -> {
                    play(list.get(RandomUtils.nextInt(0, list.size())).toFile(), message);
                    if (delete) {
                        try {
                            Thread.sleep(3000);
                            discordService.deleteMessage(message);
                        } catch (DiscordException | MissingPermissionsException | InterruptedException ex) {
                            log.warn("Could not perform cleanup: {}", ex.toString());
                        }
                    }
                }, taskExecutor);
            } catch (IOException e1) {
                log.warn("Could not create list of random sounds", e1);
            }
        }
    }

    private void play(File source, IMessage message) {
        try {
            Optional<IVoiceChannel> voiceChannel = message.getAuthor().getVoiceChannel();
            if (voiceChannel.isPresent()) {
                synchronized (lock) {
                    voiceChannel.get().join();
                    if (!voiceChannel.get().isConnected()) {
                        CountDownLatch latch = new CountDownLatch(1);
                        // block until we connect
                        CompletableFuture.runAsync(() -> {
                            while (!voiceChannel.get().isConnected()) {
                                try {
                                    Thread.sleep(250);
                                } catch (InterruptedException e) {
                                    log.warn("Interrupted while waiting for connection", e);
                                }
                            }
                            latch.countDown();
                        }, taskExecutor);
                        latch.await(5, TimeUnit.SECONDS);
                    }
                    AudioChannel audioChannel = voiceChannel.get().getAudioChannel();
                    //playing.put(source, voiceChannel.get());
                    //queueCounter.computeIfAbsent(voiceChannel.get(), k -> new AtomicInteger(0)).incrementAndGet();
                    Integer count = settingsService.getSettings().getPlayCount().getOrDefault(source.getName(), 0);
                    settingsService.getSettings().getPlayCount().put(source.getName(), count + 1);
                    audioChannel.queueFile(source);
                }
            }
        } catch (DiscordException | InterruptedException e) {
            log.warn("Unable to play sound bite", e);
        }
    }

//    @EventSubscriber
//    public void onAudioStopped(AudioStopEvent event) {
//        Optional<File> source = event.getFileSource();
//        if (source.isPresent()) {
//            CompletableFuture.runAsync(() -> {
//                synchronized (lock) {
//                    IVoiceChannel channel = playing.get(source.get());
//                    if (channel != null && queueCounter.get(channel).decrementAndGet() == 0) {
//                        try {
//                            Thread.sleep(750);
//                        } catch (InterruptedException ignore) {
//                        }
//                        log.debug("Leaving {}", DiscordUtil.toString(channel));
//                        channel.leave();
//                        playing.remove(source.get());
//                    }
//                }
//            }, taskExecutor);
//        }
//    }

}
