package com.edocs.signing;

import java.time.Instant;
import java.util.UUID;

import com.edocs.document.Document;
import com.edocs.document.Party;
import com.edocs.identity.SignatureLevel;
import com.edocs.identity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Non-repudiation record: who signed, when, from where, at which eIDAS level, over which content hash.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "signatures")
public class Signature {

    @Id
    @Column(name = "sig_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id")
    private Document document;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", unique = true)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id")
    private User signer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private SignatureLevel type;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String certificate;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "evidence_hash", nullable = false, length = 64)
    private String evidenceHash;
}
