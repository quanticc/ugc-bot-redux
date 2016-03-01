package com.ugcleague.ops.domain.document;

import java.time.ZonedDateTime;

public abstract class Subscription extends AbstractAuditingEntity {

    private boolean enabled = true;
    private Mode mode = Mode.ALWAYS;
    private ZonedDateTime start;
    private ZonedDateTime finish;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public void setStart(ZonedDateTime start) {
        this.start = start;
    }

    public ZonedDateTime getFinish() {
        return finish;
    }

    public void setFinish(ZonedDateTime finish) {
        this.finish = finish;
    }

    /**
     * The subscription mode if it's enabled.
     */
    public enum Mode {
        /**
         * Subscribed to all events.
         */
        ALWAYS,
        /**
         * Subscribed to events only during a given period of time.
         */
        TIME_INCLUSIVE,
        /**
         * Subscribed to all events except those on a given period of time.
         */
        TIME_EXCLUSIVE;
    }
}
