package com.ugcleague.ops.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NuclearResponse {

    private Run current = new Run();
    private Run previous = new Run();

    public Run getCurrent() {
        return current;
    }

    public void setCurrent(Run current) {
        this.current = current;
    }

    public Run getPrevious() {
        return previous;
    }

    public void setPrevious(Run previous) {
        this.previous = previous;
    }

    public static class Run {
        @JsonProperty("char")
        private int character;
        @JsonProperty("lasthit")
        private int lastDamagedBy;
        private int world;
        @JsonProperty("level")
        private int area;
        private int crown;
        @JsonProperty("wepA")
        private int weapon1;
        @JsonProperty("wepB")
        private int weapon2;
        private int skin;
        private int ultra;
        @JsonProperty("charlvl")
        private int characterLevel;
        @JsonProperty("loops")
        private int loop;
        private boolean win;
        private String mutations = "";
        private int kills;
        private int health;
        @JsonProperty("steamid")
        private long steamId;
        private String type = "";
        private long timestamp;

        public int getCharacter() {
            return character;
        }

        public void setCharacter(int character) {
            this.character = character;
        }

        public int getLastDamagedBy() {
            return lastDamagedBy;
        }

        public void setLastDamagedBy(int lastDamagedBy) {
            this.lastDamagedBy = lastDamagedBy;
        }

        public int getWorld() {
            return world;
        }

        public void setWorld(int world) {
            this.world = world;
        }

        public int getArea() {
            return area;
        }

        public void setArea(int area) {
            this.area = area;
        }

        public int getCrown() {
            return crown;
        }

        public void setCrown(int crown) {
            this.crown = crown;
        }

        public int getWeapon1() {
            return weapon1;
        }

        public void setWeapon1(int weapon1) {
            this.weapon1 = weapon1;
        }

        public int getWeapon2() {
            return weapon2;
        }

        public void setWeapon2(int weapon2) {
            this.weapon2 = weapon2;
        }

        public int getSkin() {
            return skin;
        }

        public void setSkin(int skin) {
            this.skin = skin;
        }

        public int getUltra() {
            return ultra;
        }

        public void setUltra(int ultra) {
            this.ultra = ultra;
        }

        public int getCharacterLevel() {
            return characterLevel;
        }

        public void setCharacterLevel(int characterLevel) {
            this.characterLevel = characterLevel;
        }

        public int getLoop() {
            return loop;
        }

        public void setLoop(int loop) {
            this.loop = loop;
        }

        public boolean isWin() {
            return win;
        }

        public void setWin(boolean win) {
            this.win = win;
        }

        public String getMutations() {
            return mutations;
        }

        public void setMutations(String mutations) {
            this.mutations = mutations;
        }

        public int getKills() {
            return kills;
        }

        public void setKills(int kills) {
            this.kills = kills;
        }

        public int getHealth() {
            return health;
        }

        public void setHealth(int health) {
            this.health = health;
        }

        public long getSteamId() {
            return steamId;
        }

        public void setSteamId(long steamId) {
            this.steamId = steamId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
