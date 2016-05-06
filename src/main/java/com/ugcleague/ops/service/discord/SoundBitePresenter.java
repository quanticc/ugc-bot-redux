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

    private OptionSpec<Void> soundbitesEnableSpec;
    private OptionSpec<Void> soundbitesDisableSpec;
    private OptionSpec<String> soundbitesNonOptionSpec;
    private OptionSpec<String> soundbitesRemoveSpec;
    private OptionSpec<Void> soundbitesListSpec;
    private OptionSpec<String> soundbitesRandomSpec;

    @Autowired
    public SoundBitePresenter(DiscordService discordService, SoundBiteRepository soundBiteRepository,
                              SettingsService settingsService, CommandService commandService) {
        this.discordService = discordService;
        this.soundBiteRepository = soundBiteRepository;
        this.settingsService = settingsService;
        this.commandService = commandService;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
        OptionParser parser = newParser();
        soundbitesEnableSpec = parser.accepts("enable", "Enable soundbites in this guild");
        soundbitesDisableSpec = parser.accepts("disable", "Disable soundbites in this guild");
        soundbitesRemoveSpec = parser.accepts("remove", "Remove a soundbite")
            .withRequiredArg().describedAs("alias");
        soundbitesListSpec = parser.accepts("list", "List all soundbites");
        soundbitesRandomSpec = parser.accepts("random", "Define a folder as the pool of random sounds")
            .withRequiredArg().describedAs("folder");
        soundbitesNonOptionSpec = parser.nonOptions("A series of aliases of a given audio path (last argument)");
        Map<String, String> aliases = newAliasesMap();
        aliases.put("enable", "--enable");
        aliases.put("disable", "--disable");
        aliases.put("remove", "--remove");
        aliases.put("random", "--random");
        aliases.put("list", "--list");
        commandService.register(CommandBuilder.startsWith(".sounds").master()
            .description("Manage soundbite settings")
            .command(this::soundbites).parser(parser).optionAliases(aliases).originReplies().build());
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
            // .sounds <alias...> <path>
            String path = nonOptions.get(nonOptions.size() - 1);
            List<String> aliases = nonOptions.subList(0, nonOptions.size() - 1);
            List<SoundBite> soundBites = aliases.stream().map(s -> newSoundBite(s, path)).collect(Collectors.toList());
            soundBiteRepository.save(soundBites);
        } else {
            return null;
        }
        return ":ok_hand:";
    }

    private SoundBite newSoundBite(String alias, String path) {
        SoundBite bite = new SoundBite();
        bite.setId(alias);
        bite.setPath(path);
        return bite;
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent e) {
        IMessage message = e.getMessage();
        if (!message.getChannel().isPrivate()
            && settingsService.getSettings().getSoundBitesWhitelist().contains(message.getGuild().getID())) {
            if (message.getContent().equals("!w")) {
                String dirStr = settingsService.getSettings().getRandomSoundDir();
                if (dirStr != null) {
                    Path dir = Paths.get(dirStr);
                    try {
                        List<Path> list = Files.list(dir).filter(p -> !Files.isDirectory(p))
                            .collect(Collectors.toList());
                        play(list.get(RandomUtils.nextInt(0, list.size())).toFile(), message);
                    } catch (IOException e1) {
                        log.warn("Could not create list of random sounds", e1);
                    }
                }
            } else {
                Optional<SoundBite> soundBite = soundBiteRepository.findById(message.getContent());
                if (soundBite.isPresent()) {
                    File source = new File(soundBite.get().getPath());
                    if (!source.exists()) {
                        log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                    }
                    play(source, message);
                }
            }
        }
    }

    private void play(File source, IMessage message) {
        try {
            Optional<IVoiceChannel> voiceChannel = message.getAuthor().getVoiceChannel();
            if (voiceChannel.isPresent()) {
                if (!voiceChannel.get().isConnected()) {
                    voiceChannel.get().join();
                }
                AudioChannel audioChannel = voiceChannel.get().getAudioChannel();
                audioChannel.queueFile(source);
            }
            // cleanup message
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting");
                }
            }).thenRun(() -> {
                try {
                    discordService.deleteMessage(message);
                } catch (DiscordException | MissingPermissionsException | InterruptedException e) {
                    log.warn("Could not perform cleanup: {}", e.toString());
                }
            });
        } catch (DiscordException e) {
            log.warn("Unable to play sound bite", e);
        }
    }

}
