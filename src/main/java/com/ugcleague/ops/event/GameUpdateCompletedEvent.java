package com.ugcleague.ops.event;

import com.ugcleague.ops.service.util.UpdateResultMap;
import org.springframework.context.ApplicationEvent;

public class GameUpdateCompletedEvent extends ApplicationEvent {

    private int version;

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public GameUpdateCompletedEvent(UpdateResultMap source) {
        super(source);
    }

    public GameUpdateCompletedEvent toVersion(int version) {
        this.version = version;
        return this;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public UpdateResultMap getSource() {
        return (UpdateResultMap) super.getSource();
    }
}
