package com.edocs.document;

import java.time.Instant;
import java.util.UUID;

import com.edocs.identity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@Table(name = "document_shares")
public class DocumentShare {

    public enum Access {
        @JsonProperty("view") VIEW,
        @JsonProperty("comment") COMMENT,
        @JsonProperty("sign") SIGN
    }

    @Id
    @Column(name = "share_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id")
    private Document document;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Access access;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_by")
    private User sharedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DocumentShare(Document document, String email, Access access, User sharedBy) {
        this.id = UUID.randomUUID();
        this.document = document;
        this.email = email;
        this.access = access;
        this.sharedBy = sharedBy;
        this.createdAt = Instant.now();
    }
}
