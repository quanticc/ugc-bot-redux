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

        NuclearRun runData = runDataMap.computeIfAbsent(stream.getId(), k -> new NuclearRun());
        NuclearStats runStats = runStatsMap.computeIfAbsent(stream.getId(), k -> new NuclearStats());
        NuclearRun current = NuclearRun.fromResponse(response.getCurrent());
        NuclearRun previous = NuclearRun.fromResponse(response.getPrevious());

        // Has the player died?
        if (previous.getTimestamp() > 0 && previous.getTimestamp() == runData.getTimestamp()) {
            onDeath(stream, previous);
            runData.setTimestamp(0);
        }

        // If we have a current run
        if (current.getTimestamp() > 0) {
            //if (data.running)
            if (!running.contains(key)) {
                runStats.healed(8);
                onNewRun(stream, current);
                onNewLevel(stream, current);
                running.add(key);
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
        }
    }

    private void announce(NuclearStream stream, String message) {
        if (message != null && !message.isEmpty()) {
            announcePresenter.announce(stream.getPublisher().getId(), message, true, false);
        }
    }

    private void announce(NuclearStream stream, List<Response> responseList, Map<String, String> context) {
        String response = responseList.get(RandomUtils.nextInt(0, responseList.size())).result;
//        double total = responseList.stream().map(r -> (double) 1 / r.rarity).reduce(0D, Double::sum);
//        double random = Math.random();
//        double sum = 0D;
//        String response = responseList.get(responseList.size() - 1).result;
//        for (Response item : responseList) {
//            double chance = ((double) 1 / item.rarity) / total;
//            sum += chance;
//            if (random < sum) {
//                response = item.result;
//            }
//        }
        if (context == null) {
            announcePresenter.announce(stream.getPublisher().getId(), response, true, false);
        } else {
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(new StringReader(response), "response");
            try {
                StringWriter writer = new StringWriter();
                mustache.execute(writer, context).flush();
                announcePresenter.announce(stream.getPublisher().getId(), writer.toString(), true, false);
            } catch (IOException e) {
                log.warn("Could not create response", e);
            }
        }
    }

    private void onHealed(NuclearStream stream, int amount) {
        List<Response> responses = Arrays.asList(
            new Response(1, ""),
            new Response(1.5, "Oh yeah"),
            new Response(1.5, "Nice"),
            new Response(1.5, "Great"),
            new Response(1.5, "Fantastic"),
            new Response(1.5, "Awesome"),
            new Response(3, "Thanks for the health"),
            new Response(3, "Finally some health"),
            new Response(3, "That hit the spot")
        );
        announce(stream, responses, null);
        log.info("Player {} was healed by {} points", stream.getId(), amount);
    }

    private void onHurt(NuclearStream stream, int enemyId) {
        String enemy = NuclearThrone.ENEMIES.get(enemyId);
        List<Response> responses = Arrays.asList(
            new Response(0.5, ""),
            new Response(1, "Ouch"),
            new Response(1, "Ow"),
            new Response(2, "Got hit by {{an_enemy}}"),
            new Response(1, "This motherfucking {{enemy}}"),
            new Response(2, "Motherfucker {{enemy}} bit me"),
            new Response(3, "I've had it with these {{enemy}}s in this motherfucking level")
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
        announce(stream, "Motherfucking " + u);
        log.info("Player {} got {} ultra", stream.getId(), c, u);
    }

    private void onNewCrown(NuclearStream stream, int crown) {
        String c = NuclearThrone.CROWNS.get(crown - 1);
        announce(stream, "Chose " + c);
        log.info("Player {} chose {}", stream.getId(), c);
    }

    private void onNewMutation(NuclearStream stream, int mutation) {
        String m = NuclearThrone.MUTATIONS.get(mutation);
        announce(stream, "Motherfucking " + m);
        log.info("Player {} took {}", stream.getId(), m);
    }

    private void onWeaponPickup(NuclearStream stream, int weapon) {
        String w = NuclearThrone.WEAPONS.get(weapon);
        announce(stream, "Motherfucking " + w);
        log.info("Player {} picked up {}", stream.getId(), w);
    }

    private void onNewRun(NuclearStream stream, NuclearRun run) {
        String c = NuclearThrone.CHARACTERS.get(run.getCharacter());
        List<String> messages = NuclearThrone.CHARACTER_TIPS.get(run.getCharacter());
        if (messages != null) {
            announce(stream, messages.get(RandomUtils.nextInt(0, messages.size())));
        }
        log.info("Player {} started a new run with {}", stream.getId(), c);
    }

    private void onNewLevel(NuclearStream stream, NuclearRun run) {
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
            List<Response> responses = Arrays.asList(
                new Response(1, "Crown Vault ain't no country I've ever heard of. They speak English in Vaults?"),
                new Response(2, "I've had it with these motherfucking vaults in this motherfucking game.")
            );
            announce(stream, responses, null);
            return;
        }

        String boss = getBossName(world, area, loop);
        String worldName = getWorldName(world);

        if (boss != null) {
            List<Response> responses = Arrays.asList(
                new Response(1, "Entered {{boss}} level"),
                new Response(2, "Watch out for the {{boss}}")
            );
            Map<String, String> context = new HashMap<>();
            context.put("boss", boss);
            announce(stream, responses, context);
        } else if (world == 0) {
            announce(stream, NuclearThrone.LOOP_TIPS.get(RandomUtils.nextInt(0, NuclearThrone.LOOP_TIPS.size())));
        } else if (area == 1) {
            announce(stream, "Entered the " + worldName);
        } else {
            List<String> messages = NuclearThrone.WORLD_TIPS.get(world);
            announce(stream, messages.get(RandomUtils.nextInt(0, messages.size())));
        }
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

    private static class Response {
        private double rarity = 1D;
        private String result = "";

        public Response(double rarity, String result) {
            this.rarity = rarity;
            this.result = result;
        }
    }

    private void onDeath(NuclearStream stream, NuclearRun run) {
        String enemy = NuclearThrone.ENEMIES.get(run.getLastDamagedBy());
        String level = run.getLevel();
        List<Response> responses = Arrays.asList(
            new Response(1, "Died to {{enemy}}"),
            new Response(4, "Fuck you"),
            new Response(4, "Come on"),
            new Response(4, "Wake the fuck up"),
            new Response(4, "Oh I'm sorry did I break your concentration"),
            new Response(4, "See I told you you should've killed that bitch"),
            new Response(4, "I don't remember asking you a god damn thing")
        );
        Map<String, String> context = new HashMap<>();
        context.put("enemy", avsAnRule.suggestAorAn(enemy));
        announce(stream, responses, context);
        log.info("Player {} died to a {} on {}", stream.getId(), enemy, level);
    }

    private String humanizeRun(NuclearResponse.Run run) {
        StringBuilder builder = new StringBuilder();
        builder.append("Character: ").append(NuclearThrone.CHARACTERS.get(run.getCharacter())).append(", ");
        builder.append("Last Hit: ").append(NuclearThrone.ENEMIES.get(run.getLastDamagedBy())).append(", ");
        builder.append("Level: ").append(run.getWorld()).append(run.getArea()).append(", ");
        builder.append("Crown: ").append(NuclearThrone.CROWNS.get(run.getCrown())).append(", ");
        builder.append("Weapon 1: ").append(NuclearThrone.WEAPONS.get(run.getWeapon1())).append(", ");
        builder.append("Weapon 2: ").append(NuclearThrone.WEAPONS.get(run.getWeapon2())).append(", ");
        builder.append("B-Skin: ").append(run.getSkin() == 1 ? "Yes" : "No").append(", ");
        if (run.getUltra() > 0) {
            builder.append("Ultra: ").append(NuclearThrone.ULTRAS.get(run.getCharacter() - 1).get(run.getUltra() - 1)).append(", ");
        }
        builder.append("Character Level: ").append(run.getCharacterLevel()).append(", ");
        builder.append("Loop: ").append(run.getLoop()).append(", ");
        builder.append("Win: ").append(run.isWin()).append(", ");
        List<String> mutations = new ArrayList<>();
        for (int i = 0; i < run.getMutations().length(); i++) {
            if (run.getMutations().charAt(i) == '1') {
                mutations.add(NuclearThrone.MUTATIONS.get(i));
            }
        }
        builder.append("Mutations: ").append(mutations.toString()).append(", ");
        builder.append("Kills: ").append(run.getKills()).append(", ");
        builder.append("Health: ").append(run.getHealth()).append(", ");
        builder.append("Type: ").append(run.getType()).append(", ");
        builder.append("Timestamp: ").append(run.getTimestamp()).append(", ");
        return builder.toString();
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