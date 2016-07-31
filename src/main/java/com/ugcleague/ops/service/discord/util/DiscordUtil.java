package com.ugcleague.ops.service.discord.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RequestBuffer;

import java.util.List;

public class DiscordUtil {

    private static final Logger log = LoggerFactory.getLogger(DiscordUtil.class);

    public static String toString(IMessage message) {
        return String.format("[%s] %s: %s", toString(message.getChannel()), toString(message.getAuthor()), message.getContent());
    }

    public static String toString(IUser user) {
        return String.format("%s#%s (%s)", user.getName(), user.getDiscriminator(), user.getID());
    }

    public static String toString(IChannel channel) {
        if (channel.isPrivate()) {
            return String.format("*PM* (%s)", channel.getID());
        } else {
            return String.format("%s/%s (%s)", channel.getGuild().getName(), channel.getName(), channel.getID());
        }
    }

    public static String toString(IGuild guild) {
        return String.format("%s (%s)", guild.getName(), guild.getID());
    }

    public static String toString(IRole role) {
        return String.format("%s/%s (%s)", role.getGuild().getName(), role.getName().replace("@", "@\u200B"), role.getID());
    }

    public static void deleteInBatch(IChannel channel, List<IMessage> toDelete) {
        log.info("Preparing to delete {} messages from {}", toDelete.size(), DiscordUtil.toString(channel));
        if (toDelete.isEmpty()) {
            log.info("No messages to delete");
        } else {
            log.info("Preparing to delete {} messages from {}", toDelete.size(), DiscordUtil.toString(channel));
            for (int x = 0; x < (toDelete.size() / 100) + 1; x++) {
                List<IMessage> subList = toDelete.subList(x * 100, Math.min(toDelete.size(), (x + 1) * 100));
                RequestBuffer.request(() -> {
                    try {
                        channel.getMessages().bulkDelete(subList);
                    } catch (MissingPermissionsException | DiscordException e) {
                        log.warn("Failed to delete message", e);
                    }
                    return null;
                });
            }
        }
    }

    private DiscordUtil() {}
}
