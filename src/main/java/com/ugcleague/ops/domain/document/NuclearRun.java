package com.ugcleague.ops.domain.document;

import com.ugcleague.ops.service.util.NuclearThrone;
import com.ugcleague.ops.web.rest.NuclearResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NuclearRun {

    private int health;
    private boolean skin;
    private int character;
    private int crown;
    private List<Integer> mutations = new ArrayList<>();
    private List<Integer> weapons = new ArrayList<>();
    private long timestamp;
    private int kills;
    private int ultra;
    private int lastDamagedBy;
    private int area;
    private int world;
    private int loop;
    private String level = "";

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public boolean isSkin() {
        return skin;
    }

    public void setSkin(boolean skin) {
        this.skin = skin;
    }

    public int getCharacter() {
        return character;
    }

    public void setCharacter(int character) {
        this.character = character;
    }

    public int getCrown() {
        return crown;
    }

    public void setCrown(int crown) {
        this.crown = crown;
    }

    public List<Integer> getMutations() {
        return mutations;
    }

    public void setMutations(List<Integer> mutations) {
        this.mutations = mutations;
    }

    public List<Integer> getWeapons() {
        return weapons;
    }

    public void setWeapons(List<Integer> weapons) {
        this.weapons = weapons;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getUltra() {
        return ultra;
    }

    public void setUltra(int ultra) {
        this.ultra = ultra;
    }

    public int getLastDamagedBy() {
        return lastDamagedBy;
    }

    public void setLastDamagedBy(int lastDamagedBy) {
        this.lastDamagedBy = lastDamagedBy;
    }

    public int getArea() {
        return area;
    }

    public void setArea(int area) {
        this.area = area;
    }

    public int getWorld() {
        return world;
    }

    public void setWorld(int world) {
        this.world = world;
    }

    public int getLoop() {
        return loop;
    }

    public void setLoop(int loop) {
        this.loop = loop;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public static NuclearRun fromResponse(NuclearResponse.Run nrr) {
        NuclearRun run = new NuclearRun();
        if (nrr != null) {
            run.health = nrr.getHealth();
            run.skin = nrr.getSkin() == 1;
            run.character = nrr.getCharacter();
            run.crown = nrr.getCrown();
            run.mutations = parseMutations(nrr.getMutations());
            run.weapons = parseWeapons(nrr.getWeapon1(), nrr.getWeapon2());
            run.timestamp = nrr.getTimestamp();
            run.kills = nrr.getKills();
            run.ultra = nrr.getUltra();
            run.lastDamagedBy = nrr.getLastDamagedBy();
            run.area = nrr.getArea();
            run.world = nrr.getWorld();
            run.loop = nrr.getLoop();
            run.level = String.format("L%d %d-%d", run.loop, run.world, run.area);
        }
        return run;
    }

    private static List<Integer> parseMutations(String mutationText) {
        List<Integer> mutations = new ArrayList<>();
        for (int i = 0; i < mutationText.length(); i++) {
            if (mutationText.charAt(i) == '1') {
                mutations.add(i);
            }
        }
        return mutations;
    }

    private static List<Integer> parseWeapons(int weapon1, int weapon2) {
        if (weapon1 > 0) {
            if (weapon2 > 0) {
                if (weapon1 < weapon2) {
                    return Arrays.asList(weapon1, weapon2);
                } else {
                    return Arrays.asList(weapon2, weapon1);
                }
            } else {
                return Collections.singletonList(weapon1);
            }
        } else if (weapon2 > 0) {
            return Collections.singletonList(weapon2);
        }
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Character: ").append(NuclearThrone.CHARACTERS.get(character)).append("\n");
        builder.append("LastDamagedBy: ").append(NuclearThrone.ENEMIES.get(lastDamagedBy)).append("\n");
        builder.append("Level: ").append(level).append("\n");
        if (crown > 0) {
            builder.append("Crown: ").append(NuclearThrone.CROWNS.get(crown - 1)).append("\n");
        }
        String weapons = this.weapons.stream()
            .map(i -> NuclearThrone.WEAPONS.get(i))
            .collect(Collectors.joining(", "));
        builder.append("Weapons: ").append(weapons).append("\n");
        builder.append("BSkin: ").append(skin).append("\n");
        if (ultra > 0) {
            builder.append("Ultra: ").append(NuclearThrone.ULTRAS.get(character - 1).get(ultra - 1)).append("\n");
        }
        String mutations = this.mutations.stream()
            .map(i -> NuclearThrone.MUTATIONS.get(i))
            .collect(Collectors.joining(", "));
        builder.append("Mutations: ").append(mutations).append("\n");
        builder.append("Kills: ").append(kills).append("\n");
        builder.append("Health: ").append(health).append("\n");
        builder.append("Timestamp: ").append(Instant.ofEpochSecond(timestamp)).append("\n");
        return builder.toString();
    }
}
