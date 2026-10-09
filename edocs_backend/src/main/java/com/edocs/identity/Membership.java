package com.edocs.identity;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.edocs.security.Permission;
import com.edocs.security.RolePermissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Organization composes Membership (User * -- 1 Organization via memberOf).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memberships")
public class Membership {

    @Id
    @Column(name = "membership_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Membership(User user, Organization organization, LocalDate joinedAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.organization = organization;
        this.joinedAt = joinedAt;
    }

    // Permissions are derived from the user's role rather than stored, so the RBAC matrix stays the single source.
    public Set<Permission> permissions() {
        return RolePermissions.of(user.getRole());
    }
}
