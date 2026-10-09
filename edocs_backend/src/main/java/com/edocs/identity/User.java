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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends Person {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 10)
    private KycStatus kycStatus = KycStatus.NONE;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public User(String fullName, String email, Role role) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.email = email.trim().toLowerCase();
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
