package com.ugcleague.ops.event;

import org.springframework.context.ApplicationEvent;

public class DiscordAnnounceEvent extends ApplicationEvent {
    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public DiscordAnnounceEvent(Object source) {
        super(source);
    }
}
