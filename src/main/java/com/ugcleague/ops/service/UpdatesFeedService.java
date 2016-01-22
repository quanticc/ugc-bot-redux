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
import com.ugcleague.ops.event.FeedUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

@Service
@Transactional
public class UpdatesFeedService {

    private static final Logger log = LoggerFactory.getLogger(UpdatesFeedService.class);

    private final SteamCondenserService condenserService;
    private final LeagueProperties leagueProperties;
    private final ApplicationEventPublisher publisher;
    private final TaskService taskService;

    private FeedFetcher feedFetcher;
    private URL url;

    @Autowired
    public UpdatesFeedService(SteamCondenserService condenserService, LeagueProperties leagueProperties,
                              ApplicationEventPublisher publisher, TaskService taskService) {
        this.condenserService = condenserService;
        this.leagueProperties = leagueProperties;
        this.publisher = publisher;
        this.taskService = taskService;
    }

    @PostConstruct
    private void init() {
        String cachePath = leagueProperties.getFeed().getCacheDir();
        String urlSpec = leagueProperties.getFeed().getUrl();
        try {
            Files.createDirectories(Paths.get(cachePath));
        } catch (IOException e) {
            log.warn("Could not create cache path", e);
        }
        FeedFetcherCache feedInfoCache = new DiskFeedInfoCache(cachePath);
        feedFetcher = new HttpURLFeedFetcher(feedInfoCache);
        try {
            url = new URL(urlSpec);
        } catch (MalformedURLException e) {
            log.warn("Feed URL is not valid", e);
        }
        feedFetcher.addFetcherEventListener(new FetcherListenerImpl());
        //taskService.registerTask("refreshUpdatesFeed", 20000, 600000, this::refreshUpdatesFeed);
    }

    @Scheduled(initialDelay = 20000, fixedRate = 600000)
    public void refreshUpdatesFeed() {
        String task = "refreshUpdatesFeed";
        log.debug("==== Retrieving latest updates feed ====");
        //taskService.scheduleNext(task);
        if (taskService.isEnabled(task)) {
            try {
                feedFetcher.retrieveFeed(url);
            } catch (IllegalArgumentException | IOException | FeedException | FetcherException e) {
                log.warn("Could not fetch latest updates feed: {}", e.toString());
            }
        }
    }

    private class FetcherListenerImpl implements FetcherListener {
        @Override
        public void fetcherEvent(FetcherEvent event) {
            final String eventType = event.getEventType();
            if (FetcherEvent.EVENT_TYPE_FEED_POLLED.equals(eventType)) {
                log.debug("[hlds_announce] Feed polled from {}", event.getUrlString());
            } else if (FetcherEvent.EVENT_TYPE_FEED_RETRIEVED.equals(eventType)) {
                Instant lastPublishedDate = event.getFeed().getPublishedDate().toInstant();
                log.debug("[hlds_announce] Feed retrieved. Published at {}", lastPublishedDate);
                // news post might not be related to TF2!! always check before dispatching event
                if (condenserService.getLatestVersion() < condenserService.queryLatestVersion()) {
                    publisher.publishEvent(new FeedUpdatedEvent(event.getFeed()));
                }
            } else if (FetcherEvent.EVENT_TYPE_FEED_UNCHANGED.equals(eventType)) {
                log.debug("[hlds_announce] Feed unchanged");
            }
        }
    }

}
