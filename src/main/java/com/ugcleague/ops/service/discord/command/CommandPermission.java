package com.ugcleague.ops.service.discord.command;

public enum CommandPermission {

    NONE("command.user"), SUPPORT("command.support"), MASTER("command.master");

    private final String key;

    CommandPermission(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
