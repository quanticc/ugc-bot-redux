package com.ugcleague.ops.service.discord.command;

import static com.ugcleague.ops.util.Util.toLowerCamelCase;

/**
 * How to match the command key for valid invocations.
 */
public enum MatchType {
    /**
     * The input message must be equal to the command key
     */
    EQUALS,
    /**
     * The input message must begin with the command key.
     */
    STARTS_WITH,
    /**
     * Allows combined behavior between EQUALS and STARTS_WITH, allowing null arg invocations as valid.
     */
    ANY;

    @Override
    public String toString() {
        return toLowerCamelCase(name());
    }
}
