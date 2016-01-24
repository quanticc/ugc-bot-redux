package com.ugcleague.ops.service.discord.command;

public enum CommandPermission {

    NONE(0), SUPPORT(10), MASTER(100);

    private final int level;

    CommandPermission(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
