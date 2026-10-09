package com.edocs.security;

import static com.edocs.security.Permission.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.edocs.identity.Role;

// RBAC matrix: role -> permissions. Enforced by @PreAuthorize on every protected endpoint.
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MATRIX = Map.of(
            Role.ADMIN, EnumSet.allOf(Permission.class),
            Role.LEGAL, EnumSet.of(DOCUMENT_CREATE, DOCUMENT_EDIT, DOCUMENT_SIGN, DOCUMENT_SHARE, WORKFLOW_MANAGE, AUDIT_READ, TEMPLATE_MANAGE),
            Role.SIGNER, EnumSet.of(DOCUMENT_SIGN),
            Role.AUDITOR, EnumSet.of(AUDIT_READ, AUDIT_EXPORT));

    private RolePermissions() {
    }

    public static Set<Permission> of(Role role) {
        return EnumSet.copyOf(MATRIX.get(role));
    }

    public static boolean can(Role role, Permission permission) {
        return role != null && MATRIX.get(role).contains(permission);
    }

    // ROLE_<role> plus one authority per permission, e.g. "document:create".
    public static Collection<GrantedAuthority> authorities(Role role) {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        MATRIX.get(role).forEach(p -> list.add(new SimpleGrantedAuthority(p.authority())));
        return list;
    }
}
