package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.domain.document.SoundBite;
import com.ugcleague.ops.repository.mongo.SoundBiteRepository;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.command.CommandBuilder;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
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

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        soundbitesNonOptionSpec = parser.nonOptions("A series of aliases of a given audio path (last argument)");
        Map<String, String> aliases = newAliasesMap();
        commandService.register(CommandBuilder.startsWith(".sounds").master()
            .description("Manage soundbite settings")
            .command(this::soundbites).parser(parser).optionAliases(aliases).build());
    }

    private String soundbites(IMessage message, OptionSet optionSet) {
        if (message.getChannel().isPrivate()) {
            return "Does not work with private channels yet";
        }
        List<String> nonOptions = optionSet.valuesOf(soundbitesNonOptionSpec);
        if (optionSet.has(soundbitesEnableSpec)) {
            // .sounds enable
            settingsService.getSettings().getSoundBitesWhitelist().add(message.getChannel().getGuild().getID());
        } else if (optionSet.has(soundbitesDisableSpec)) {
            // .sounds disable
            settingsService.getSettings().getSoundBitesWhitelist().remove(message.getChannel().getGuild().getID());
        } else if (nonOptions.size() > 1) {
            // .sounds <alias...> <path>
            String path = nonOptions.get(nonOptions.size() - 1);
            List<String> aliases = nonOptions.subList(0, nonOptions.size() - 1);
            List<SoundBite> soundBites = aliases.stream().map(s -> newSoundBite(s, path)).collect(Collectors.toList());
            soundBiteRepository.save(soundBites);
        } else {
            return null;
        }
        return ":ok_hand";
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
            Optional<SoundBite> soundBite = soundBiteRepository.findById(message.getContent());
            if (soundBite.isPresent()) {
                try {
                    Optional<IVoiceChannel> voiceChannel = message.getAuthor().getVoiceChannel();
                    if (voiceChannel.isPresent()) {
                        if (!voiceChannel.get().isConnected()) {
                            voiceChannel.get().join();
                        }
                        AudioChannel audioChannel = voiceChannel.get().getAudioChannel();
                        File source = new File(soundBite.get().getPath());
                        if (source.exists()) {
                            audioChannel.queueFile(source);
                        } else {
                            log.warn("Invalid source: {} -> {}", soundBite.get().getId(), source);
                        }
                    }
                } catch (DiscordException ex) {
                    log.warn("Unable to play sound bite", ex);
                }
            }
        }
    }

}
