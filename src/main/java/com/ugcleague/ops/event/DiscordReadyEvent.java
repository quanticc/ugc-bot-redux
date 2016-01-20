package com.ugcleague.ops.event;

import org.springframework.context.ApplicationEvent;

public class DiscordReadyEvent extends ApplicationEvent {
    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public DiscordReadyEvent(Object source) {
        super(source);
    }
}
