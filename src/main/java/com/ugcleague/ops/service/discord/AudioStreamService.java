package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.api.EventSubscriber;
import sx.blah.discord.handle.AudioChannel;
import sx.blah.discord.handle.impl.events.AudioUnqueuedEvent;

import javax.annotation.PostConstruct;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

@Service
public class AudioStreamService implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AudioStreamService.class);

    private final DiscordService discordService;

    @Autowired
    public AudioStreamService(DiscordService discordService) {
        this.discordService = discordService;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
    }

    public boolean queueFromYouTube(AudioChannel audioChannel, String id) {
        String name = System.getProperty("os.name").contains("Windows") ? "youtube-dl.exe" : "youtube-dl";
        ProcessBuilder builder = new ProcessBuilder(name, "-q", "-f", "worstaudio",
            "--exec", "ffmpeg -hide_banner -nostats -loglevel panic -y -i {} -vn -q:a 5 -f mp3 pipe:1", "-o",
            "%(id)s", id);
        try {
            Process process = builder.start();
            try {
                audioChannel.queue(AudioSystem.getAudioInputStream(process.getInputStream()));
                return true;
            } catch (UnsupportedAudioFileException e) {
                log.warn("Could not queue audio", e);
                process.destroyForcibly();
            }
        } catch (IOException e) {
            log.warn("Could not start process", e);
        }
        return false;
    }



    @EventSubscriber
    public void onAudioDequeue(AudioUnqueuedEvent event) {
        AudioInputStream stream = event.getStream();
        try {
            stream.close();
            log.debug("Stream {} was closed", stream.toString());
        } catch (IOException e) {
            log.warn("Could not close audio stream", e);
        }
    }
}
