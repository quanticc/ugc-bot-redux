package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.config.LeagueProperties;
import com.ugcleague.ops.domain.Subscriber;
import com.ugcleague.ops.domain.document.*;
import com.ugcleague.ops.repository.LegacyPublisherRepository;
import com.ugcleague.ops.repository.SubscriberRepository;
import com.ugcleague.ops.repository.mongo.PublisherRepository;
import com.ugcleague.ops.service.DiscordCacheService;
import com.ugcleague.ops.service.DiscordService;
import com.ugcleague.ops.service.discord.util.DiscordSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sx.blah.discord.handle.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IUser;

import javax.annotation.PostConstruct;
import java.util.Optional;

@Service
@Transactional
public class SupportPingPresenter implements DiscordSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SupportPingPresenter.class);

    private final LeagueProperties properties;
    private final DiscordCacheService cacheService;
    private final PublisherRepository publisherRepository;
    private final DiscordService discordService;
    private final LegacyPublisherRepository oldPublisherRepository;
    private final SubscriberRepository subscriberRepository;

    @Autowired
    public SupportPingPresenter(LeagueProperties properties, DiscordCacheService cacheService,
                                PublisherRepository publisherRepository, DiscordService discordService,
                                LegacyPublisherRepository oldPublisherRepository,
                                SubscriberRepository subscriberRepository) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.publisherRepository = publisherRepository;
        this.discordService = discordService;
        this.oldPublisherRepository = oldPublisherRepository;
        this.subscriberRepository = subscriberRepository;
    }

    @PostConstruct
    private void configure() {
        discordService.subscribe(this);
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        if (publisherRepository.count() == 0) {
            log.debug("Preparing to migrate from previous schema");
            if (!properties.getDiscord().getSupport().getChannels().isEmpty()) {
                for (com.ugcleague.ops.domain.Publisher oldPublisher : oldPublisherRepository.findAllEagerly()) {
                    Publisher publisher = publisherRepository.findById(oldPublisher.getName())
                        .orElseGet(() -> migrateToNewPublisher(oldPublisher));
                    publisher = publisherRepository.save(publisher);

                    for (Subscriber subscriber : subscriberRepository.findAll()) {
                        IUser user = discordService.getClient().getUserByID(subscriber.getUserId());
                        Optional<DiscordUser> userOptional = cacheService.findUserById(subscriber.getUserId());
                        if (user != null || userOptional.isPresent()) {
                            String id = userOptional.map(DiscordUser::getId).orElseGet(() -> user == null ? null : user.getID());
                            String name = userOptional.map(DiscordUser::getName).orElseGet(() -> user == null ? null : user.getName());
                            log.debug("[{}] Migrating subscription of user {}: {}", publisher.getId(), id, name);
                            DiscordUser discordUser = userOptional.orElseGet(() -> cacheService.getOrCreateUser(user));
                            UserSubscription subscription = new UserSubscription();
                            subscription.setUser(discordUser);
                            subscription.setStart(subscriber.getStart());
                            subscription.setFinish(subscriber.getFinish());
                            subscription.setEnabled(oldPublisher.getSubscribers().contains(subscriber));
                            subscription.setMode(subModeFromBoolean(subscriber.getEnabled()));
                            publisher.getUserSubscriptions().add(subscription);
                            publisherRepository.save(publisher);
                        } else {
                            IChannel channel = discordService.getClient().getChannelByID(subscriber.getUserId());
                            Optional<DiscordChannel> channelOptional = cacheService.findChannelById(subscriber.getUserId());
                            if (channel != null || channelOptional.isPresent()) {
                                String id = channelOptional.map(DiscordChannel::getId).orElseGet(() -> channel == null ? null : channel.getID());
                                String name = channelOptional.map(DiscordChannel::getName).orElseGet(() -> channel == null ? null : channel.getName());
                                log.debug("[{}] Migrating subscription of channel {}: {}", publisher.getId(), id, name);
                                DiscordChannel discordChannel = channelOptional.orElseGet(() -> cacheService.getOrCreateChannel(channel));
                                ChannelSubscription subscription = new ChannelSubscription();
                                subscription.setChannel(discordChannel);
                                subscription.setStart(subscriber.getStart());
                                subscription.setFinish(subscriber.getFinish());
                                subscription.setEnabled(oldPublisher.getSubscribers().contains(subscriber));
                                subscription.setMode(subModeFromBoolean(subscriber.getEnabled()));
                                publisher.getChannelSubscriptions().add(subscription);
                                publisherRepository.save(publisher);
                            } else {
                                log.warn("[{}] Could not find user or channel by id: {}", publisher.getId(), subscriber.getUserId());
                            }
                        }
                    }
                }
            } else {
                log.warn("No support channel data in properties to migrate publisher config");
            }
        }
    }

    private Subscription.Mode subModeFromBoolean(Boolean value) {
        if (value == null) {
            return Subscription.Mode.ALWAYS;
        } else if (value) {
            return Subscription.Mode.TIME_INCLUSIVE;
        } else {
            return Subscription.Mode.TIME_EXCLUSIVE;
        }
    }

    private Publisher migrateToNewPublisher(com.ugcleague.ops.domain.Publisher publisher) {
        Publisher pub = new Publisher();
        if (publisher.getName().equals("support")) {
            pub.setId("tf2");
            pub.setChannelId(properties.getDiscord().getSupport().getChannels().get(0));
        } else {
            pub.setId(publisher.getName());
        }
        return pub;
    }
}
