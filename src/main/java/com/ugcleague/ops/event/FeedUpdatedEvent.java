package com.ugcleague.ops.event;

import com.rometools.rome.feed.synd.SyndFeed;
import org.springframework.context.ApplicationEvent;

public class FeedUpdatedEvent extends ApplicationEvent {

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public FeedUpdatedEvent(SyndFeed source) {
        super(source);
    }

    @Override
    public SyndFeed getSource() {
        return (SyndFeed) super.getSource();
    }
}
