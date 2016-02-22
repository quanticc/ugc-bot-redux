package com.ugcleague.ops.service.discord.command;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import sx.blah.discord.handle.obj.IMessage;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Helps to build a Command
 */
public class CommandBuilder {

    /**
     * This command will react to all messages starting with the given key. Note that if a user sends exactly the key as
     * input, it will not be recognized as command, so this is used for commands that explicitly require arguments and
     * are not falling through a help reply on a no argument invocation. Use {@link CommandBuilder#anyMatch(String)} for
     * a more permissive invocation way.
     *
     * @param key the key used to trigger the command
     * @return a builder to incrementally define a command
     */
    public static CommandBuilder startsWith(String key) {
        CommandBuilder builder = new CommandBuilder();
        return builder.key(key).matchType(MatchType.STARTS_WITH);
    }

    /**
     * This command will react to all messages containing exactly this key. This means that this command will not accept
     * arguments.
     *
     * @param key the key used to trigger the command
     * @return a builder to incrementally define a command
     */
    public static CommandBuilder equalsTo(String key) {
        CommandBuilder builder = new CommandBuilder();
        return builder.key(key).matchType(MatchType.EQUALS);
    }

    /**
     * This command will react to all messages containing either exactly this key or are starting with the key.
     *
     * @param key the key used to trigger the command
     * @return a builder to incrementally define a command
     */
    public static CommandBuilder anyMatch(String key) {
        CommandBuilder builder = new CommandBuilder();
        return builder.key(key).matchType(MatchType.ANY);
    }

    private MatchType matchType = MatchType.STARTS_WITH;
    private String key;
    private String description = "";
    private OptionParser parser = new OptionParser();
    private BiFunction<IMessage, OptionSet, String> command = (m, o) -> null;
    private CommandPermission permission = CommandPermission.MASTER; // by default
    private boolean queued = false;
    private boolean mention = false;
    private ReplyMode replyMode = ReplyMode.PRIVATE; // by default
    private boolean persistStatus = false;
    private Map<String, String> optionAliases = null;

    private CommandBuilder() {
        parser.allowsUnrecognizedOptions();
    }

    private CommandBuilder matchType(MatchType matchType) {
        this.matchType = matchType;
        return this;
    }

    private CommandBuilder key(String key) {
        this.key = key;
        return this;
    }

    /**
     * The command's description, used in command listing and help.
     *
     * @param description this command's description
     * @return this builder
     */
    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * The parser used to define arguments this command will use.
     *
     * @param parser a jopt-simple options parser
     * @return this builder
     */
    public CommandBuilder parser(OptionParser parser) {
        this.parser = parser;
        return this;
    }

    /**
     * Do not use a parser with this command. Actual argument parsing will have to be performed in the command
     * execution method.
     *
     * @return this builder
     */
    public CommandBuilder noParser() {
        this.parser = null;
        return this;
    }

    /**
     * The action that this commands perform.
     *
     * @param command a function of a discord message with a resolved argument set that returns a command reply
     * @return this builder
     */
    public CommandBuilder command(BiFunction<IMessage, OptionSet, String> command) {
        this.command = command;
        return this;
    }

    /**
     * The permission required to execute this command as a String equivalent to one of {@link CommandPermission}.
     *
     * @param permission the command permission in String form
     * @return this builder
     */
    public CommandBuilder permission(String permission) {
        for (CommandPermission perm : CommandPermission.values()) {
            if (perm.getKey().equals(permission) || perm.getKey().equals("command." + permission)) {
                this.permission = perm;
                return this;
            }
        }
        return this;
    }

    /**
     * The permission required to execute this command.
     *
     * @param permission the command permission
     * @return this builder
     */
    public CommandBuilder permission(CommandPermission permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Equivalent to {@link #permission(String)} using "master" as argument.
     *
     * @return this builder
     */
    public CommandBuilder master() {
        this.permission = CommandPermission.MASTER;
        return this;
    }

    /**
     * Equivalent to {@link #permission(String)} using "support" as argument.
     *
     * @return this builder
     */
    public CommandBuilder support() {
        this.permission = CommandPermission.SUPPORT;
        return this;
    }

    /**
     * Equivalent to {@link #permission(String)} using "none" as argument.
     *
     * @return this builder
     */
    public CommandBuilder unrestricted() {
        this.permission = CommandPermission.NONE;
        return this;
    }

    /**
     * This command's execution will be queued in a separate thread and throttled so that a same user cannot invoke it
     * in rapid succession.
     *
     * @return this builder
     */
    public CommandBuilder queued() {
        this.queued = true;
        return this;
    }

    /**
     * When the command gives a reply back to the author, append a mention. Best used for long running commands that
     * reply on a public or busy channel.
     *
     * @return this builder
     */
    public CommandBuilder mention() {
        this.mention = true;
        return this;
    }

    /**
     * Always reply to the user with a private message. This is the default behavior.
     *
     * @return this builder
     */
    public CommandBuilder privateReplies() {
        this.replyMode = ReplyMode.PRIVATE;
        return this;
    }

    /**
     * Reply to the user with a private message unless the channel's permission is equal or greater than the command's
     * permission level.
     *
     * @return this builder
     */
    public CommandBuilder permissionReplies() {
        this.replyMode = ReplyMode.PERMISSION_BASED;
        return this;
    }

    /**
     * Always reply to the user on the same channel used for invocation.
     *
     * @return this builder
     */
    public CommandBuilder originReplies() {
        this.replyMode = ReplyMode.ORIGIN;
        return this;
    }

    /**
     * This command status message should be kept in a memory storage after the command has given its standard reply.
     * Used for commands that perform background operations in a separate thread.
     *
     * @return this builder
     */
    public CommandBuilder persistStatus() {
        this.persistStatus = true;
        return this;
    }

    /**
     * Allow this command to treat non-options as options by replacing them with the values in the given map.
     *
     * @param optionAliases an alias map where the keys are the non-options to convert and the values the aliased options
     *                      in the getopt form (must begin with - or --).
     * @return this builder
     * @throws IllegalArgumentException if the option alias map contract is not fulfilled.
     */
    public CommandBuilder withOptionAliases(Map<String, String> optionAliases) {
        if (optionAliases.values().stream().anyMatch(v -> !v.startsWith("-"))) {
            throw new IllegalArgumentException("Option alias map must have all its values starting with - or --");
        }
        this.optionAliases = optionAliases;
        return this;
    }

    /**
     * Construct the command made with the current settings.
     *
     * @return a valid command that can be registered to the service
     */
    public Command build() {
        return new Command(matchType, key, description, parser, command, permission,
            queued, replyMode, mention, persistStatus, optionAliases);
    }
}
