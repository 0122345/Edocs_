package com.edocs.document;

import java.time.LocalDate;
import java.util.UUID;

import com.edocs.identity.Organization;
import com.edocs.identity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Document metadata (PostgreSQL). The body is encrypted in MongoDB with the key wrapped in `encryptedKey`.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documents")
@Inheritance(strategy = InheritanceType.JOINED)
public class Document extends ContentItem {

    @Id
    @Column(name = "doc_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, length = 80)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private DocFormat format = DocFormat.PDF;

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocStatus status = DocStatus.DRAFT;

    @Column(name = "encrypted_key", nullable = false, length = 255)
    private String encryptedKey;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "hold_until")
    private LocalDate holdUntil;

    @Column(length = 64)
    private String sha256;

    @Column(name = "anchor_tx", length = 80)
    private String anchorTx;

    @Column(name = "size_kb", nullable = false)
    private int sizeKb = 1;

    @Column(nullable = false)
    private int pages = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public Document(Organization organization, User owner, String title, String category) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.owner = owner;
        this.title = title;
        this.category = category;
    }
}
