package com.ugcleague.ops.web.websocket.event;

import com.ugcleague.ops.domain.GameServer;
import org.springframework.context.ApplicationEvent;

public class ConsoleDetachedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    public ConsoleDetachedEvent(GameServer server) {
        super(server);
    }

    @Override
    public GameServer getSource() {
        return (GameServer) super.getSource();
    }

}
