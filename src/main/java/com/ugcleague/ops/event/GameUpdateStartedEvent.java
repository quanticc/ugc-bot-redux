package com.ugcleague.ops.event;

import org.springframework.context.ApplicationEvent;

public class GameUpdateStartedEvent extends ApplicationEvent {
    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public GameUpdateStartedEvent(Object source) {
        super(source);
    }
}
