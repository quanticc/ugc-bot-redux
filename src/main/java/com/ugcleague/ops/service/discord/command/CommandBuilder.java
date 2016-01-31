package com.ugcleague.ops.service.discord.command;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import sx.blah.discord.handle.obj.IMessage;

import java.util.function.BiFunction;

public class CommandBuilder {

    public static CommandBuilder startsWith(String key) {
        CommandBuilder builder = new CommandBuilder();
        return builder.key(key).matchType(MatchType.STARTS_WITH);
    }

    public static CommandBuilder equalsTo(String key) {
        CommandBuilder builder = new CommandBuilder();
        return builder.key(key).matchType(MatchType.EQUALS);
    }

    public static CommandBuilder combined(String key) {
        CommandBuilder builder = new CommandBuilder();
        return builder.key(key).matchType(MatchType.COMBINED);
    }

    private MatchType matchType = MatchType.STARTS_WITH;
    private String key;
    private String description = "";
    private OptionParser parser = new OptionParser();
    private BiFunction<IMessage, OptionSet, String> command = (m, o) -> null;
    private int permissionLevel = CommandPermission.MASTER.getLevel(); // by default
    private boolean queued = false;
    private boolean mention = false;
    private ReplyMode replyMode = ReplyMode.PRIVATE; // by default
    private boolean persistStatus = false;
    private boolean experimental = false;

    private CommandBuilder() {

    }

    public CommandBuilder matchType(MatchType matchType) {
        this.matchType = matchType;
        return this;
    }

    public CommandBuilder key(String key) {
        this.key = key;
        return this;
    }

    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    public CommandBuilder parser(OptionParser parser) {
        this.parser = parser;
        return this;
    }

    public CommandBuilder command(BiFunction<IMessage, OptionSet, String> command) {
        this.command = command;
        return this;
    }

    public CommandBuilder permission(String permission) {
        CommandPermission parsedPermission = CommandPermission.valueOf(permission.toUpperCase());
        this.permissionLevel = (parsedPermission == null ? CommandPermission.NONE.getLevel() : parsedPermission.getLevel());
        return this;
    }

    public CommandBuilder permission(CommandPermission permission) {
        this.permissionLevel = permission.getLevel();
        return this;
    }

    public CommandBuilder permission(int permissionLevel) {
        this.permissionLevel = permissionLevel;
        return this;
    }

    public CommandBuilder queued() {
        this.queued = true;
        return this;
    }

    public CommandBuilder mention() {
        this.mention = true;
        return this;
    }

    public CommandBuilder privateReplies() {
        this.replyMode = ReplyMode.PRIVATE;
        return this;
    }

    public CommandBuilder permissionReplies() {
        this.replyMode = ReplyMode.WITH_PERMISSION;
        return this;
    }

    public CommandBuilder originReplies() {
        this.replyMode = ReplyMode.ORIGIN;
        return this;
    }

    public CommandBuilder persistStatus() {
        this.persistStatus = true;
        return this;
    }

    public CommandBuilder experimental() {
        this.experimental = true;
        return this;
    }

    public Command build() {
        return new Command(matchType, key, description, parser, command, permissionLevel,
            queued, replyMode, mention, persistStatus, experimental);
    }
}
