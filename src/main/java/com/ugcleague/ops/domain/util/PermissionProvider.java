package com.ugcleague.ops.domain.util;

import com.ugcleague.ops.domain.document.Permission;

import java.util.Set;

/**
 * Represents an entity that provides permissions.
 */
public interface PermissionProvider {

    Set<Permission> getAllowed();

    void setAllowed(Set<Permission> allowed);

    Set<Permission> getDenied();

    void setDenied(Set<Permission> denied);
}
