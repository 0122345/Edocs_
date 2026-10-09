package com.edocs.audit;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Append-only audit entry (MongoDB). `hash` chains to the previous entry of the same organization.
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document("audit_logs")
@CompoundIndexes({
        @CompoundIndex(name = "ux_org_seq", def = "{'orgId': 1, 'seq': 1}", unique = true),
        @CompoundIndex(name = "ix_org_at", def = "{'orgId': 1, 'at': -1}")
})
public class AuditLog {

    @Id
    private String id;
    private String orgId;
    private long seq;
    private AuditKind kind;
    private String event;
    private String entityType;
    @Indexed
    private String entityId;
    private String documentName;
    private String actorId;
    private String actor;
    private String origin;
    private Instant at;
    private AuditStatus status;
    private String prevHash;
    private String hash;
}
