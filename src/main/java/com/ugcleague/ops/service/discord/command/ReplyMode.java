package com.ugcleague.ops.service.discord.command;

import static com.ugcleague.ops.util.Util.toLowerCamelCase;

/**
 * The behavior to use on command replies.
 */
public enum ReplyMode {

    /**
     * Always reply privately to the author. This is the default mode for a command, to provide output security.
     */
    PRIVATE,
    /**
     * Reply to a private channel unless the origin channel's permission level is equal or higher than the command's
     * permission level, in which case reply back to invocation origin channel. This avoids leaking output on sensitive
     * command invoked on public (or unrestricted join, lower permission) channels.
     */
    WITH_PERMISSION,
    /**
     * Reply back to the same channel used to invoke the command. Use this for non-sensitive outputs. For the user to
     * keep track of the answers in a busy channel, combine this with mention on reply.
     *
     * @see Command#isMention()
     */
    ORIGIN;

    @Override
    public String toString() {
        return toLowerCamelCase(name());
    }
}
