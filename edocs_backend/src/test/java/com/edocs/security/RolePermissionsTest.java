package com.edocs.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.edocs.identity.Role;

class RolePermissionsTest {

    @Test
    void adminHasEveryPermission() {
        assertThat(RolePermissions.of(Role.ADMIN)).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void signerCanOnlySign() {
        assertThat(RolePermissions.of(Role.SIGNER)).containsExactly(Permission.DOCUMENT_SIGN);
    }

    @Test
    void auditorIsReadOnly() {
        assertThat(RolePermissions.of(Role.AUDITOR)).containsExactlyInAnyOrder(Permission.AUDIT_READ, Permission.AUDIT_EXPORT);
        assertThat(RolePermissions.can(Role.AUDITOR, Permission.DOCUMENT_EDIT)).isFalse();
    }

    @Test
    void legalCannotManageMembersOrSettings() {
        assertThat(RolePermissions.can(Role.LEGAL, Permission.MEMBERS_MANAGE)).isFalse();
        assertThat(RolePermissions.can(Role.LEGAL, Permission.SETTINGS_MANAGE)).isFalse();
        assertThat(RolePermissions.can(Role.LEGAL, Permission.DOCUMENT_DELETE)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void authoritiesContainRoleAndPermissions(Role role) {
        Set<String> names = RolePermissions.authorities(role).stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
        assertThat(names).contains("ROLE_" + role.name());
        RolePermissions.of(role).forEach(p -> assertThat(names).contains(p.authority()));
    }

    // Guards against drift between the backend matrix and edocs_frontend/src/lib/rbac.ts.
    @Test
    void matrixMatchesFrontend() throws Exception {
        Path rbac = Path.of("..", "edocs_frontend", "src", "lib", "rbac.ts");
        if (!Files.exists(rbac)) {
            return;
        }
        String ts = Files.readString(rbac).replaceAll("\\s+", " ");
        for (Role role : Role.values()) {
            String key = role.name().toLowerCase();
            Matcher m = Pattern.compile(key + ": \\[(.*?)\\]").matcher(ts.substring(ts.indexOf("const matrix")));
            assertThat(m.find()).as("role %s in rbac.ts", key).isTrue();
            Set<String> frontend = Arrays.stream(m.group(1).split(","))
                    .map(s -> s.replace("'", "").trim()).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
            Set<String> backend = RolePermissions.of(role).stream().map(Permission::authority).collect(Collectors.toSet());
            assertThat(backend).as("permissions for %s", key).isEqualTo(frontend);
        }
    }
}
