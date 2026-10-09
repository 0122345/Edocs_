package com.edocs.security;

import java.util.UUID;

import com.edocs.identity.Role;

// Authenticated caller, resolved per request from the JWT subject and the live database row.
public record AuthUser(UUID userId, UUID orgId, String email, String name, Role role) {

    public boolean can(Permission permission) {
        return RolePermissions.can(role, permission);
    }
}
