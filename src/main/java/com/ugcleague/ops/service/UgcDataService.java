package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.*;
import com.ugcleague.ops.repository.mongo.UgcPlayerRepository;
import com.ugcleague.ops.repository.mongo.UgcSeasonRepository;
import com.ugcleague.ops.repository.mongo.UgcTeamRepository;
import com.ugcleague.ops.service.discord.util.RosterData;
import com.ugcleague.ops.web.rest.UgcPlayerPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UgcDataService {

    private static final Logger log = LoggerFactory.getLogger(UgcDataService.class);

    private final UgcSeasonRepository seasonRepository;
    private final UgcPlayerRepository playerRepository;
    private final UgcTeamRepository teamRepository;
    private final UgcApiClient apiClient;
    private final SteamCondenserService steamCondenserService;

    @Autowired
    public UgcDataService(UgcSeasonRepository seasonRepository, UgcPlayerRepository playerRepository,
                          UgcTeamRepository teamRepository, UgcApiClient apiClient,
                          SteamCondenserService steamCondenserService) {
        this.seasonRepository = seasonRepository;
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.apiClient = apiClient;
        this.steamCondenserService = steamCondenserService;
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

    public List<RosterData> findPlayers(List<RosterData> request) {
        return request.parallelStream()
            .map(rd -> rd.updateUgcData(apiClient.getCurrentPlayer(rd.getCommunityId())))
            .collect(Collectors.toList());
    }

    public List<UgcPlayerPage> findPlayers(Set<Long> communityIdSet) {
        return communityIdSet.parallelStream()
            .map(apiClient::getCurrentPlayer)
            .collect(Collectors.toList());
    }

    public Optional<UgcTeam> findTeamById(int id, boolean refresh) {
        Optional<UgcTeam> team = Optional.ofNullable(teamRepository.findOne(id));
        if (refresh || !team.isPresent()) {
            team = apiClient.getTeamPage(id);
        }
        if (team.isPresent()) {
            if (refresh || team.get().getRoster().isEmpty()) {
                List<UgcPlayer> roster = apiClient.getTeamRoster(id);
                roster = playerRepository.save(roster);
                team.get().setRoster(roster);
            }
            team = Optional.ofNullable(teamRepository.save(team.get()));
        }
        return team;
    }
}
