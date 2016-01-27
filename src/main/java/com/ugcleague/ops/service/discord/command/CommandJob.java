package com.ugcleague.ops.service.discord.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.IMessage;

import java.util.function.Supplier;

public class CommandJob implements Supplier<String> {

    private static final Logger log = LoggerFactory.getLogger(CommandJob.class);

    // these entities can be persisted for archiving/auditing purposes

    private final Command command;
    private final IMessage message;
    private final String args;

    private String output;

    private final long created;
    private long started;
    private long completed;

    public CommandJob(Command command, IMessage message, String args) {
        this.created = System.nanoTime();
        this.command = command;
        this.message = message;
        this.args = args;
    }

    public Command getCommand() {
        return command;
    }

    public IMessage getMessage() {
        return message;
    }

    public String getArgs() {
        return args;
    }

    public String getOutput() {
        return output;
    }

    public long getCreated() {
        return created;
    }

    public long getStarted() {
        return started;
    }

    public long getCompleted() {
        return completed;
    }

    @Override
    public String get() {
        started = System.nanoTime();
        output = command.execute(message, args);
        completed = System.nanoTime();
        return output;
    }
}
