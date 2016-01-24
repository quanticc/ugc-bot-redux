package com.ugcleague.ops.event;

import com.ugcleague.ops.service.util.DeadServerMap;
import org.springframework.context.ApplicationEvent;

public class GameServerDeathEvent extends ApplicationEvent {
    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public GameServerDeathEvent(DeadServerMap source) {
        super(source);
    }

    @Override
    public DeadServerMap getSource() {
        return (DeadServerMap) super.getSource();
    }
}
