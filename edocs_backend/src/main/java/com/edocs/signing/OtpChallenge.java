package com.edocs.signing;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One-time code for MFA sign-in or signature intent. Only a salted hash of the code is stored.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

    public enum Purpose { MFA, SIGNING }

    @Id
    @Column(name = "challenge_id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "doc_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Purpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
