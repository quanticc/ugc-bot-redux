package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sx.blah.discord.util.audio.AudioPlayer;

import javax.annotation.PostConstruct;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

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

    public boolean queueFromYouTube(AudioPlayer audioPlayer, String id) {
        String name = System.getProperty("os.name").contains("Windows") ? "youtube-dl.exe" : "youtube-dl";
        ProcessBuilder builder = new ProcessBuilder(name, "-q", "-f", "worstaudio",
            "--exec", "ffmpeg -hide_banner -nostats -loglevel panic -y -i {} -vn -q:a 5 -f mp3 pipe:1", "-o",
            "%(id)s", "--", id);
        try {
            Process process = builder.start();
            try {
                CompletableFuture.runAsync(() -> logStream(process.getErrorStream()));
                AudioPlayer.Track track = audioPlayer.queue(AudioSystem.getAudioInputStream(process.getInputStream()));
                track.getMetadata().put("url", id);
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

    private BufferedReader newProcessReader(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
    }

    private void logStream(InputStream stream) {
        try (BufferedReader input = newProcessReader(stream)) {
            String line;
            while ((line = input.readLine()) != null) {
                log.info("[yt-dl] " + line);
            }
        } catch (IOException e) {
            log.warn("Could not read from stream", e);
        }
    }
}
