package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.UgcResult;
import com.ugcleague.ops.domain.document.UgcSeason;
import com.ugcleague.ops.domain.document.UgcWeek;
import com.ugcleague.ops.repository.mongo.UgcSeasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class UgcDataService {

    private static final Logger log = LoggerFactory.getLogger(UgcDataService.class);

    private final UgcSeasonRepository seasonRepository;
    private final UgcApiClient apiClient;

    @Autowired
    public UgcDataService(UgcSeasonRepository seasonRepository, UgcApiClient apiClient) {
        this.seasonRepository = seasonRepository;
        this.apiClient = apiClient;
    }

    public Set<UgcResult> findBySeasonAndWeek(int season, int week, boolean refresh) {
        UgcSeason ugcSeason = Optional.ofNullable(seasonRepository.findOne(season)).orElseGet(UgcSeason::new);
        ugcSeason.setId(season);
        UgcWeek ugcWeek = ugcSeason.getWeeks().stream().filter(w -> w.getId() == week).findFirst().orElseGet(UgcWeek::new);
        ugcWeek.setId(week);
        Set<UgcResult> results = ugcWeek.getResults();
        if (results == null || results.isEmpty() || refresh) {
            results = apiClient.getMatchResults(season, week);
        }
        // now pack and save
        ugcWeek.setResults(results);
        ugcSeason.getWeeks().add(ugcWeek);
        seasonRepository.save(ugcSeason);
        return results;
    }
}
