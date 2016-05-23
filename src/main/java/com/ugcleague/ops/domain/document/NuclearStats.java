package com.ugcleague.ops.domain.document;

import java.util.ArrayList;
import java.util.List;

public class NuclearStats {

    private static final int DEFAULT_CHARACTER = -1;
    private static final int DEFAULT_CROWN = -1;
    private static final int DEFAULT_ULTRA = 0;
    private static final int DEFAULT_ENEMY = -1;

    private int character = DEFAULT_CHARACTER;
    private int causeOfDeath = DEFAULT_ENEMY;
    private int lastCrown = DEFAULT_CROWN;
    private int lastHurt = DEFAULT_ENEMY;
    private int lastUltra = DEFAULT_ULTRA;
    private int lastHealth = 0;
    private String diedOnLevel = "";
    private List<Integer> weapons = new ArrayList<>();
    private List<Integer> mutations = new ArrayList<>();
    private List<Integer> crowns = new ArrayList<>();

    //// ACCESSORS

    public int getCharacter() {
        return character;
    }

    public void setCharacter(int character) {
        this.character = character;
    }

    public int getCauseOfDeath() {
        return causeOfDeath;
    }

    public void setCauseOfDeath(int causeOfDeath) {
        this.causeOfDeath = causeOfDeath;
    }

    public int getLastCrown() {
        return lastCrown;
    }

    public void setLastCrown(int lastCrown) {
        this.lastCrown = lastCrown;
    }

    public int getLastHurt() {
        return lastHurt;
    }

    public void setLastHurt(int lastHurt) {
        this.lastHurt = lastHurt;
    }

    public int getLastUltra() {
        return lastUltra;
    }

    public void setLastUltra(int lastUltra) {
        this.lastUltra = lastUltra;
    }

    public int getLastHealth() {
        return lastHealth;
    }

    public void setLastHealth(int lastHealth) {
        this.lastHealth = lastHealth;
    }

    public String getDiedOnLevel() {
        return diedOnLevel;
    }

    public void setDiedOnLevel(String diedOnLevel) {
        this.diedOnLevel = diedOnLevel;
    }

    public List<Integer> getWeapons() {
        return weapons;
    }

    public void setWeapons(List<Integer> weapons) {
        this.weapons = weapons;
    }

    public List<Integer> getMutations() {
        return mutations;
    }

    public void setMutations(List<Integer> mutations) {
        this.mutations = mutations;
    }

    public List<Integer> getCrowns() {
        return crowns;
    }

    public void setCrowns(List<Integer> crowns) {
        this.crowns = crowns;
    }

    ///// CHANGE DETECTION

    public int healed(int health) {
        int diff = health - lastHealth;
        lastHealth = health;
        return diff;
    }

    public boolean weaponPickup(int weapon) {
        if (weapons.contains(weapon)) {
            return false;
        }
        weapons.add(weapon);
        return true;
    }

    public boolean mutationChoice(int mutation) {
        if (mutations.contains(mutation)) {
            return false;
        }
        mutations.add(mutation);
        return true;
    }

    public boolean crownChoice(int crown) {
        if (crown == DEFAULT_CROWN || crown == 0) {
            return false;
        }

        if (crowns.contains(crown)) {
            if (crown != lastCrown) {
                lastCrown = crown;
                return true;
            }
            return false;
        }

        crowns.add(crown);
        lastCrown = crown;

        // Skip notification for starting with "Bare head"
        if (crown == 1) {
            return false;
        }

        return true;
    }

    public boolean ultraChoice(int ultra) {
        if (ultra == 0 || ultra == lastUltra) {
            return false;
        }

        lastUltra = ultra;
        return true;
    }

    public boolean hurt(int enemyId) {
        if (enemyId == 0 || enemyId == lastHurt) {
            return false;
        }

        lastHurt = enemyId;
        return true;
    }

    public void killed(int causeOfDeath, String diedOnLevel) {
        this.causeOfDeath = causeOfDeath;
        this.diedOnLevel = diedOnLevel;
    }

    public void newRun(int character) {
        this.character = character;
    }

    public void reset() {
        character = -1;
        causeOfDeath = -1;
        diedOnLevel = "";
        lastCrown = 1;
        lastUltra = 0;
        lastHurt = 0;
        weapons = new ArrayList<>();
        mutations = new ArrayList<>();
        crowns = new ArrayList<>();
    }
}
