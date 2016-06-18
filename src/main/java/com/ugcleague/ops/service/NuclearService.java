package com.ugcleague.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.ugcleague.ops.domain.document.NuclearRun;
import com.ugcleague.ops.domain.document.NuclearStats;
import com.ugcleague.ops.domain.document.NuclearStream;
import com.ugcleague.ops.domain.document.Publisher;
import com.ugcleague.ops.repository.mongo.NuclearStreamRepository;
import com.ugcleague.ops.repository.mongo.PublisherRepository;
import com.ugcleague.ops.service.discord.AnnouncePresenter;
import com.ugcleague.ops.service.util.NuclearThrone;
import com.ugcleague.ops.web.rest.NuclearResponse;
import org.apache.commons.lang3.RandomUtils;
import org.languagetool.JLanguageTool;
import org.languagetool.Languages;
import org.languagetool.rules.en.AvsAnRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class NuclearService {

    private static final Logger log = LoggerFactory.getLogger(NuclearService.class);

    private final PublisherRepository publisherRepository;
    private final NuclearStreamRepository nuclearStreamRepository;
    private final RestOperations restTemplate;
    private final ObjectMapper mapper;
    private final AnnouncePresenter announcePresenter;

    private final Map<String, NuclearStream> enabledStreams = new ConcurrentHashMap<>();
    private final Map<String, NuclearStats> runStatsMap = new ConcurrentHashMap<>();
    private final Map<String, NuclearRun> runDataMap = new ConcurrentHashMap<>();
    private final Set<String> running = new ConcurrentSkipListSet<>();
    private final AvsAnRule avsAnRule = new AvsAnRule(JLanguageTool.getMessageBundle(Languages.getLanguageForShortName("en")));

    @Autowired
    public NuclearService(PublisherRepository publisherRepository, NuclearStreamRepository nuclearStreamRepository,
                          RestOperations restTemplate, ObjectMapper mapper, AnnouncePresenter announcePresenter) {
        this.publisherRepository = publisherRepository;
        this.nuclearStreamRepository = nuclearStreamRepository;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.announcePresenter = announcePresenter;
    }

    public Publisher getOrCreatePublisher(String name) {
        return publisherRepository.findById(name).orElse(createPublisher(name));
    }

    private Publisher createPublisher(String name) {
        Publisher p = new Publisher();
        p.setId(name);
        return publisherRepository.save(p);
    }

    public NuclearStream save(NuclearStream nuclearStream) {
        return nuclearStreamRepository.save(nuclearStream);
    }

    public NuclearStream getOrCreateNuclearStream(String id64, String key, Publisher publisher) {
        return nuclearStreamRepository.findById(id64)
            .orElse(createNuclearStream(id64, key, publisher));
    }

    private NuclearStream createNuclearStream(String id64, String key, Publisher publisher) {
        NuclearStream n = new NuclearStream();
        n.setId(id64);
        n.setKey(key);
        n.setPublisher(publisher);
        return nuclearStreamRepository.save(n);
    }

    public Optional<NuclearStream> findStreamByPublisher(String publisher) {
        return nuclearStreamRepository.findAll().stream().filter(n -> n.getPublisher().getId().equals(publisher))
            .findAny();
    }

    public void start(NuclearStream stream) {
        enabledStreams.put(stream.getId(), stream);
    }

    public void stop(NuclearStream stream) {
        enabledStreams.remove(stream.getId());
    }

    public void delete(NuclearStream stream) {
        Publisher publisher = stream.getPublisher();
        nuclearStreamRepository.delete(stream);
        publisherRepository.delete(publisher);
    }

    @Scheduled(cron = "*/5 * * * * ?")
    public void tick() {
        for (NuclearStream stream : enabledStreams.values()) {
            String s = stream.getId();
            String key = stream.getKey();
            ResponseEntity<String> response = restTemplate
                .getForEntity("http://tb-api.xyz/stream/get?s=" + s + "&key=" + key, String.class);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                String entity = response.getBody();
                try {
                    NuclearResponse nuclearResponse = mapper.readValue(entity, NuclearResponse.class);
                    processUpdate(stream, nuclearResponse);
                    //log.info("{}", humanizeRun(nuclearResponse.getCurrent()));
                } catch (IOException e) {
                    log.warn("Could not parse response", e);
                }
            }
        }
    }

    private void processUpdate(NuclearStream stream, NuclearResponse response) {
        String key = stream.getId();

        NuclearRun runData = runDataMap.computeIfAbsent(key, k -> new NuclearRun());
        NuclearStats runStats = runStatsMap.computeIfAbsent(key, k -> new NuclearStats());
        NuclearRun current = NuclearRun.fromResponse(response.getCurrent());
        NuclearRun previous = NuclearRun.fromResponse(response.getPrevious());

        // Has the player died?
        if (previous.getTimestamp() > 0 && previous.getTimestamp() == runData.getTimestamp()) {
            onDeath(stream, previous);
            runData.setTimestamp(0);
        }

        // If we have a current run
        if (current.getTimestamp() > 0) {
            if (!running.contains(key)) {
                running.add(key);
                runStats = new NuclearStats();
                runStatsMap.put(key, runStats);
                runStats.healed(8);
                onNewRun(stream, current);
                onNewLevel(stream, current);
            } else {
                current.getWeapons().stream()
                    .filter(runStats::weaponPickup)
                    .forEach(i -> onWeaponPickup(stream, i));
                current.getMutations().stream()
                    .filter(runStats::mutationChoice)
                    .forEach(i -> onNewMutation(stream, i));
                if (runStats.crownChoice(current.getCrown())) {
                    onNewCrown(stream, current.getCrown());
                }
                if (runStats.ultraChoice(current.getUltra())) {
                    onNewUltra(stream, current.getCharacter(), current.getUltra());
                }
                if (runStats.hurt(current.getLastDamagedBy())) {
                    onHurt(stream, current.getLastDamagedBy());
                }
                int amount = runStats.healed(current.getHealth());
                if (amount > 0) {
                    onHealed(stream, amount);
                }

                // reached new level
                if (!runData.getLevel().equals(current.getLevel())) {
                    onNewLevel(stream, current);
                }
            }

            runDataMap.put(key, current);
        } else {
            running.remove(key);
            runDataMap.remove(key);
            runStatsMap.remove(key);
        }
    }

    private String announce(NuclearStream stream, String message) {
        if (message != null && !message.isEmpty()) {
            announcePresenter.announce(stream.getPublisher().getId(), message, true, false);
        }
        return message;
    }

    private String announce(NuclearStream stream, List<String> responseList) {
        return announce(stream, responseList, null);
    }

    private String announce(NuclearStream stream, List<String> responseList, Map<String, String> context) {
        String response = responseList.get(RandomUtils.nextInt(0, responseList.size()));
        if (context == null) {
            announcePresenter.announce(stream.getPublisher().getId(), response, true, false);
        } else {
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(new StringReader(response), "response");
            try {
                StringWriter writer = new StringWriter();
                mustache.execute(writer, context).flush();
                response = writer.toString();
                announcePresenter.announce(stream.getPublisher().getId(), response, true, false);
            } catch (IOException e) {
                log.warn("Could not create response", e);
            }
        }
        return response;
    }

    private void onHealed(NuclearStream stream, int amount) {
        List<String> responses = Arrays.asList(
            "",
            "Oh yeah",
            "Nice",
            "Great",
            "Fantastic",
            "Awesome",
            "Thanks for the health",
            "Finally some health",
            "That hit the spot"
        );
        announce(stream, responses, null);
        log.info("Player {} was healed by {} points", stream.getId(), amount);
    }

    private void onHurt(NuclearStream stream, int enemyId) {
        String enemy = NuclearThrone.ENEMIES.get(enemyId);
        List<String> responses = Arrays.asList(
            "",
            "Ouch",
            "Got hit by {{an_enemy}}",
            "This motherfucking {{enemy}}",
            "Motherfucker {{enemy}} bit me",
            "I've had it with these {{enemy}}s in this motherfucking level"
        );
        Map<String, String> context = new HashMap<>();
        context.put("an_enemy", avsAnRule.suggestAorAn(enemy));
        context.put("enemy", enemy);
        announce(stream, responses, context);
        log.info("Player {} was hurt by a {}", stream.getId(), enemy);
    }

    private void onNewUltra(NuclearStream stream, int character, int ultra) {
        String u = NuclearThrone.ULTRAS.get(character - 1).get(ultra - 1);
        String c = NuclearThrone.CHARACTERS.get(character);
        List<String> tips = NuclearThrone.ULTRA_TIPS.get(c);
        announce(stream, "Motherfucking " + u + (tips != null ? ". " + tips.get(RandomUtils.nextInt(0, tips.size())) : ""));
        log.info("Player {} got {} ultra", stream.getId(), c, u);
    }

    private void onNewCrown(NuclearStream stream, int crown) {
        String c = NuclearThrone.CROWNS.get(crown - 1);
        announce(stream, "Chose " + c);
        log.info("Player {} chose {}", stream.getId(), c);
    }

    private void onNewMutation(NuclearStream stream, int mutation) {
        String m = NuclearThrone.MUTATIONS.get(mutation);
        announce(stream, NuclearThrone.MUTATION_TIPS.getOrDefault(m, "Motherfucking " + m));
        log.info("Player {} took {}", stream.getId(), m);
    }

    private void onWeaponPickup(NuclearStream stream, int weapon) {
        String w = NuclearThrone.WEAPONS.get(weapon);
        announce(stream, Arrays.asList("Motherfucking " + w,
            NuclearThrone.WEAPON_TIPS.getOrDefault(w, "Motherfucking " + w)));
        log.info("Player {} picked up {}", stream.getId(), w);
    }

    private void onNewRun(NuclearStream stream, NuclearRun run) {
        resetAllHistory(run);
        String c = NuclearThrone.CHARACTERS.get(run.getCharacter());
        List<String> messages = NuclearThrone.CHARACTER_TIPS.get(c);
        if (messages != null) {
            announce(stream, messages.get(RandomUtils.nextInt(0, messages.size())));
        }
        List<String> modeTips = NuclearThrone.MODE_TIPS.get(run.getType());
        if (modeTips != null) {
            announce(stream, modeTips.get(RandomUtils.nextInt(0, modeTips.size())));
        }
        log.info("Player {} started a new {} run with {}", stream.getId(), run.getType(), c);
    }

    private void onNewLevel(NuclearStream stream, NuclearRun run) {
        String type = "onNewLevel";
        int world = run.getWorld();
        int area = run.getArea();
        int loop = run.getLoop();

        log.info("Player {} entered level {}", stream.getId(), run.getLevel());
        announcePresenter.announce(stream.getPublisher().getId(), run.toString());

        if (run.getHealth() <= 2) {
            announce(stream, NuclearThrone.LOW_HEALTH_TIPS.get(RandomUtils.nextInt(0,
                NuclearThrone.LOW_HEALTH_TIPS.size())));
        }

        if (world == 100) {
            List<String> responses = Arrays.asList(
                "Crown Vault ain't no country I've ever heard of. They speak English in Vaults?",
                "I've had it with these motherfucking vaults in this motherfucking game."
            );
            announce(stream, responses, null);
            return;
        }

        String boss = getBossName(world, area, loop);
        Map<String, String> context = new HashMap<>();

        if (boss != null) {
            context.put("boss", boss);
        }

        String worldName = getWorldName(world);

        if (world == 0) {
            resetHistory(run, type);
            announce(stream, NuclearThrone.LOOP_TIPS.get(RandomUtils.nextInt(0, NuclearThrone.LOOP_TIPS.size())));
            return;
        }

        List<String> pool = new ArrayList<>();

        if (boss != null) {
            pool.add("Entered {{boss}} level");
            pool.add("Watch out for the {{boss}}");
        }

        if (area == 1) {
            pool.add("Entered the " + worldName);
        }

        pool.addAll(NuclearThrone.WORLD_TIPS.get(world));
        if (pool.size() > 1) {
            pool.removeIf(s -> getHistory(run, type).contains(s));
        }
        String msg = announce(stream, pool, context);
        getHistory(run, type).add(msg);
    }

    private Set<String> getHistory(NuclearRun run, String key) {
        return run.getAnnounceHistory().computeIfAbsent(key, k -> new HashSet<>());
    }

    private void resetHistory(NuclearRun run, String key) {
        run.getAnnounceHistory().remove(key);
    }

    private void resetAllHistory(NuclearRun run) {
        run.getAnnounceHistory().clear();
    }

    private String getWorldName(int world) {
        return NuclearThrone.WORLDS.get(world);
    }

    private String getBossName(int world, int area, int loop) {
        if (loop > 0) {
            switch (world) {
                case 2:
                    return "Ball mom";
                case 4:
                    return "Hyper Crystal";
                case 6:
                    return "Technomancer";
            }
        }

        if (area == 3) {
            switch (world) {
                case 1:
                    return "Big Bandit";
                case 3:
                    return "Big Dog";
                case 5:
                    return "Little Hunter";
                case 7:
                    return "Nuclear Throne";
                case 106:
                    return "Captain";
            }
        }

        // TODO: Throne 2
        return null;
    }

    private void onDeath(NuclearStream stream, NuclearRun run) {
        String enemy = NuclearThrone.ENEMIES.get(run.getLastDamagedBy());
        String level = run.getLevel();
        List<String> responses = Arrays.asList(
            "Died to {{enemy}}",
            "Fuck you",
            "Come on",
            "Wake the fuck up",
            "Oh I'm sorry did I break your concentration",
            "See I told you you should've killed that bitch",
            "I don't remember asking you a god damn thing"
        );
        Map<String, String> context = new HashMap<>();
        context.put("enemy", avsAnRule.suggestAorAn(enemy));
        announce(stream, responses, context);
        log.info("Player {} died to a {} on {}", stream.getId(), enemy, level);
    }

    public String getRunInfo(NuclearStream stream) {
        NuclearRun run = runDataMap.get(stream.getId());
        if (run == null) {
            return "No run data";
        } else {
            return run.toString();
        }
    }
}
