package com.ugcleague.ops.domain.enumeration;

/**
 * The FileGroupType enumeration.
 */
public enum FileGroupType {
    GENERAL, MAPS, CFG;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
