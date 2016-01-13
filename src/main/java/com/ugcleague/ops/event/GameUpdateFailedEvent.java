package com.ugcleague.ops.event;

import com.ugcleague.ops.domain.GameServer;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class GameUpdateFailedEvent extends ApplicationEvent {

    private List<GameServer> servers;

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public GameUpdateFailedEvent(Object source) {
        super(source);
    }

    public GameUpdateFailedEvent causedBy(List<GameServer> servers) {
        this.servers = servers;
        return this;
    }

    public List<GameServer> getServers() {
        return servers;
    }
}
