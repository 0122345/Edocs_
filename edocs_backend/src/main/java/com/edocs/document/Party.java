package com.edocs.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "parties")
public class Party {

    @Id
    @Column(name = "party_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PartyRole role;

    @Column(name = "signature_order", nullable = false)
    private int signatureOrder;

    @Column(name = "signed_at")
    private Instant signedAt;

    public Party(String name, String email, PartyRole role, int signatureOrder) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email.trim().toLowerCase();
        this.role = role;
        this.signatureOrder = signatureOrder;
    }
}
