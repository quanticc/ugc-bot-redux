package com.ugcleague.ops.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

public class NewGameVersionAvailable extends ApplicationEvent {

    private Instant instant = Instant.now();
    private int version;

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public NewGameVersionAvailable(Object source) {
        super(source);
    }

    public NewGameVersionAvailable instant(Instant instant) {
        this.instant = instant;
        return this;
    }

    public NewGameVersionAvailable version(int version) {
        this.version = version;
        return this;
    }

    public Instant getInstant() {
        return instant;
    }

    public int getVersion() {
        return version;
    }
}
