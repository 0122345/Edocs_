package com.edocs.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Tenant. The diagram's `settings: JSON` is normalised into typed, CHECK-constrained columns.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "organizations")
public class Organization {

    public enum SubscriptionPlan { FREE, TEAM, ENTERPRISE }

    @Id
    @Column(name = "org_id")
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "tax_id", length = 64)
    private String taxId;

    @Column(unique = true, length = 120)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SubscriptionPlan plan = SubscriptionPlan.ENTERPRISE;

    @Column(name = "admin_email", nullable = false, length = 254)
    private String adminEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_level", nullable = false, length = 3)
    private SignatureLevel signatureLevel = SignatureLevel.QES;

    @Column(name = "retention_years", nullable = false)
    private int retentionYears = 7;

    @Column(name = "require_2fa", nullable = false)
    private boolean require2fa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Organization(String name, String domain, String adminEmail) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.domain = domain;
        this.adminEmail = adminEmail;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
