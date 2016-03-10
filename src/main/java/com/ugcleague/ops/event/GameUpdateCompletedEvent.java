package com.ugcleague.ops.event;

import com.ugcleague.ops.service.util.UpdateResultMap;
import org.springframework.context.ApplicationEvent;

public class GameUpdateCompletedEvent extends ApplicationEvent {

    private final int version;

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public GameUpdateCompletedEvent(UpdateResultMap source, int version) {
        super(source);
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public UpdateResultMap getSource() {
        return (UpdateResultMap) super.getSource();
    }
}
