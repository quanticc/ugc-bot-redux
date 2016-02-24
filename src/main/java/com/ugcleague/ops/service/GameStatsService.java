package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.LogsTfStats;
import com.ugcleague.ops.domain.document.SizzStats;
import com.ugcleague.ops.repository.mongo.LogsTfStatsRepository;
import com.ugcleague.ops.repository.mongo.SizzStatsRepository;
import com.ugcleague.ops.web.rest.LogsTfMatch;
import com.ugcleague.ops.web.rest.SizzMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;

@Service
@Transactional
public class GameStatsService {

    private static final Logger log = LoggerFactory.getLogger(GameStatsService.class);

    private final SizzStatsRepository sizzStatsRepository;
    private final LogsTfStatsRepository logsTfStatsRepository;
    private final SizzlingApiClient sizzlingApiClient;
    private final LogsTfApiClient logsTfApiClient;

    @Autowired
    public GameStatsService(SizzStatsRepository sizzStatsRepository, LogsTfStatsRepository logsTfStatsRepository,
                            SizzlingApiClient sizzlingApiClient, LogsTfApiClient logsTfApiClient) {
        this.sizzStatsRepository = sizzStatsRepository;
        this.logsTfStatsRepository = logsTfStatsRepository;
        this.sizzlingApiClient = sizzlingApiClient;
        this.logsTfApiClient = logsTfApiClient;
    }

    @PostConstruct
    private void configure() {
        log.debug("Sizzling stats cached: {}", sizzStatsRepository.count());
        log.debug("LogsTF stats cached: {}", logsTfStatsRepository.count());
    }

    public Iterable<SizzMatch> getSizzMatchIterable(long steamId64) {
        return sizzlingApiClient.getMatchIterable(steamId64);
    }

    public SizzStats getSizzStats(long id) {
        return sizzStatsRepository.findById(id).orElseGet(() -> cacheSizzStats(id));
    }

    private SizzStats cacheSizzStats(long id) {
        SizzStats stats = sizzlingApiClient.getStats(id);
        return sizzStatsRepository.save(stats);
    }

    public Iterable<LogsTfMatch> getLogsTfMatchIterable(long steamId64, int chunkSize) {
        return logsTfApiClient.getMatchIterable(steamId64, chunkSize);
    }

    public LogsTfStats getLogsTfStats(long id) {
        return logsTfStatsRepository.findById(id).orElseGet(() -> cacheLogsTfStats(id));
    }

    private LogsTfStats cacheLogsTfStats(long id) {
        LogsTfStats stats = logsTfApiClient.getStats(id);
        return logsTfStatsRepository.save(stats);
    }
}
