package com.ugcleague.ops.service.discord.util;

import sx.blah.discord.handle.obj.*;

public class DiscordUtil {

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

    private DiscordUtil() {}
}
