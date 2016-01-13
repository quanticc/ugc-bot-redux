package com.ugcleague.ops.service;

import com.rometools.fetcher.FeedFetcher;
import com.rometools.fetcher.FetcherEvent;
import com.rometools.fetcher.FetcherException;
import com.rometools.fetcher.FetcherListener;
import com.rometools.fetcher.impl.DiskFeedInfoCache;
import com.rometools.fetcher.impl.FeedFetcherCache;
import com.rometools.fetcher.impl.HttpURLFeedFetcher;
import com.rometools.rome.io.FeedException;
import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.Task;
import com.ugcleague.ops.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

@Service
public class UpdatesFeedService {

    private static final Logger log = LoggerFactory.getLogger(UpdatesFeedService.class);

    private final SteamCondenserService condenserService;
    private final TaskRepository taskRepository;
    private final LeagueProperties leagueProperties;

    private FeedFetcherCache feedInfoCache;
    private FeedFetcher feedFetcher;
    private URL url;
    private String cachePath;
    private String urlSpec;

    @Autowired
    public UpdatesFeedService(SteamCondenserService condenserService, TaskRepository taskRepository,
                              LeagueProperties leagueProperties) {
        this.condenserService = condenserService;
        this.taskRepository = taskRepository;
        this.leagueProperties = leagueProperties;
    }

    @PostConstruct
    private void init() {
        cachePath = leagueProperties.getFeed().getCacheDir();
        urlSpec = leagueProperties.getFeed().getUrl();
        try {
            Files.createDirectories(Paths.get(cachePath));
        } catch (IOException e) {
            log.warn("Could not create cache path", e);
        }
        feedInfoCache = new DiskFeedInfoCache(cachePath);
        feedFetcher = new HttpURLFeedFetcher(feedInfoCache);
        try {
            url = new URL(urlSpec);
        } catch (MalformedURLException e) {
            log.warn("Feed URL is not valid", e);
        }
        feedFetcher.addFetcherEventListener(new FetcherListenerImpl());
    }

    @Scheduled(initialDelay = 5000, fixedRate = 600000)
    public void refreshUpdatesFeed() {
        ZonedDateTime now = ZonedDateTime.now();
        log.debug("==== Retrieving latest updates feed ====");
        if (!taskRepository.findByName("refreshUpdatesFeed").map(Task::getEnabled).orElse(false)) {
            log.debug("Skipping task. Next attempt at {}", now.plusMinutes(10));
            return;
        }
        try {
            feedFetcher.retrieveFeed(url);
        } catch (IllegalArgumentException | IOException | FeedException | FetcherException e) {
            log.warn("Could not fetch latest updates feed: {}", e.toString());
        }
        log.debug("Next updates feed check at {}", now.plusSeconds(600).toString());
    }

    private class FetcherListenerImpl implements FetcherListener {
        @Override
        public void fetcherEvent(FetcherEvent event) {
            final String eventType = event.getEventType();
            if (FetcherEvent.EVENT_TYPE_FEED_POLLED.equals(eventType)) {
                log.debug("[hlds_announce] Feed polled from {}", event.getUrlString());
            } else if (FetcherEvent.EVENT_TYPE_FEED_RETRIEVED.equals(eventType)) {
                log.debug("[hlds_announce] Feed retrieved. Published at {}", event.getFeed().getPublishedDate().toInstant());
                condenserService.invalidateLatestVersion();
            } else if (FetcherEvent.EVENT_TYPE_FEED_UNCHANGED.equals(eventType)) {
                log.debug("[hlds_announce] Feed unchanged");
            }
        }
    }

}
