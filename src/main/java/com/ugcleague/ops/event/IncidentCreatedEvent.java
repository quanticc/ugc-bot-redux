package com.ugcleague.ops.event;

import com.ugcleague.ops.domain.document.Incident;
import org.springframework.context.ApplicationEvent;

public class IncidentCreatedEvent extends ApplicationEvent {
    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public IncidentCreatedEvent(Incident source) {
        super(source);
    }

    @Override
    public Incident getSource() {
        return (Incident) super.getSource();
    }
}
