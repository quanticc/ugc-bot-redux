package com.ugcleague.ops.service.discord.command;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import sx.blah.discord.handle.obj.IMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command implements Comparable<Command> {

    private static final Pattern PATTERN = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");

    private MatchType matchType;
    private String key;
    private String description;
    private OptionParser parser;
    private BiFunction<IMessage, OptionSet, String> command;
    private CommandPermission permission;
    private boolean queued;
    private ReplyMode replyMode;
    private boolean mention;
    private boolean persistStatus;
    private boolean experimental;

    public Command(MatchType matchType, String key, String description, OptionParser parser,
                   BiFunction<IMessage, OptionSet, String> command, CommandPermission permission, boolean queued,
                   ReplyMode replyMode, boolean mention, boolean persistStatus, boolean experimental) {
        Objects.requireNonNull(matchType, "Match type must not be null");
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(description, "Description must not be null");
        Objects.requireNonNull(parser, "Parser must not be null");
        Objects.requireNonNull(command, "Command must not be null");
        Objects.requireNonNull(permission, "Permission must not be null");
        Objects.requireNonNull(replyMode, "Reply mode must not be null");
        this.matchType = matchType;
        this.key = key;
        this.description = description;
        this.parser = parser;
        this.command = command;
        this.permission = permission;
        this.queued = queued;
        this.replyMode = replyMode;
        this.mention = mention;
        this.persistStatus = persistStatus;
        this.experimental = experimental;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OptionParser getParser() {
        return parser;
    }

    public void setParser(OptionParser parser) {
        this.parser = parser;
    }

    public BiFunction<IMessage, OptionSet, String> getCommand() {
        return command;
    }

    public void setCommand(BiFunction<IMessage, OptionSet, String> command) {
        this.command = command;
    }

    public CommandPermission getPermission() {
        return permission;
    }

    public void setPermission(CommandPermission permission) {
        this.permission = permission;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }

    public ReplyMode getReplyMode() {
        return replyMode;
    }

    public void setReplyMode(ReplyMode replyMode) {
        this.replyMode = replyMode;
    }

    public boolean isMention() {
        return mention;
    }

    public void setMention(boolean mention) {
        this.mention = mention;
    }

    public boolean isPersistStatus() {
        return persistStatus;
    }

    public void setPersistStatus(boolean persistStatus) {
        this.persistStatus = persistStatus;
    }

    public boolean isExperimental() {
        return experimental;
    }

    public void setExperimental(boolean experimental) {
        this.experimental = experimental;
    }

    public boolean matches(String message) {
        switch (matchType) {
            case COMBINED:
                return message.startsWith(key + " ") || message.equals(key);
            case STARTS_WITH:
                return message.startsWith(key + " ");
            case EQUALS:
            default:
                return message.equals(key);
        }
    }

    public String execute(IMessage message, String args) throws OptionException {
        if (parser.recognizedOptions().isEmpty()) {
            return command.apply(message, null);
        } else {
            return command.apply(message, args != null ? parser.parse(split(args)) : parser.parse());
        }
    }

    private String[] split(String args) {
        Matcher matcher = PATTERN.matcher(args);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group().replaceAll("\"|'", ""));
        }
        return matches.toArray(new String[matches.size()]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Command command = (Command) o;
        return Objects.equals(key, command.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "Command{" +
            "matchType=" + matchType +
            ", key='" + key + '\'' +
            ", description='" + description + '\'' +
            ", permission=" + permission +
            ", queued=" + queued +
            ", replyMode=" + replyMode +
            ", mention=" + mention +
            ", persistStatus=" + persistStatus +
            ", experimental=" + experimental +
            '}';
    }

    @Override
    public int compareTo(Command o) {
        return key.compareTo(o.key);
    }
}
