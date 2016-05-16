package com.ugcleague.ops.event;

import com.ugcleague.ops.domain.document.GameServer;
import com.ugcleague.ops.service.util.UpdateResultMap;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class GameUpdateDelayedEvent extends ApplicationEvent {

    private List<GameServer> servers;

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public GameUpdateDelayedEvent(UpdateResultMap source) {
        super(source);
    }

    public GameUpdateDelayedEvent causedBy(List<GameServer> servers) {
        this.servers = servers;
        return this;
    }

    public List<GameServer> getServers() {
        return servers;
    }

    @Override
    public UpdateResultMap getSource() {
        return (UpdateResultMap) super.getSource();
    }
}
