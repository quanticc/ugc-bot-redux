package com.ugcleague.ops.event;

import com.ugcleague.ops.domain.document.GameServer;
import org.springframework.context.ApplicationEvent;

public class ConsoleAttachedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    public ConsoleAttachedEvent(GameServer server) {
        super(server);
    }

    @Override
    public GameServer getSource() {
        return (GameServer) super.getSource();
    }

}
