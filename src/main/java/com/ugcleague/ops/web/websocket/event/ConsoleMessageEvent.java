package com.ugcleague.ops.web.websocket.event;

import com.ugcleague.ops.web.websocket.dto.ConsoleMessage;
import org.springframework.context.ApplicationEvent;

public class ConsoleMessageEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    public ConsoleMessageEvent(ConsoleMessage message) {
        super(message);
    }

    @Override
    public ConsoleMessage getSource() {
        return (ConsoleMessage) super.getSource();
    }

}
