package com.edocs.audit;

import java.time.Instant;
import java.util.List;

import com.edocs.common.Hashing;
import com.edocs.common.TimeFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

public final class AuditDtos {

    private AuditDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditLogDto(String id, AuditKind kind, String event, String document, String documentId, String actor,
            String origin, String timestamp, Instant at, String txHash, AuditStatus status, String hash) {

        public static AuditLogDto of(AuditLog e) {
            return new AuditLogDto(e.getId(), e.getKind(), e.getEvent(), e.getDocumentName(), e.getEntityId(), e.getActor(),
                    e.getOrigin(), TimeFormat.stamp(e.getAt()), e.getAt(), Hashing.shortTx(e.getHash()), e.getStatus(), e.getHash());
        }
    }

    public record AuditPage(List<AuditLogDto> items, long total, int page, int size) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChainVerification(long objects, boolean intact, long entries, Long brokenAtSeq) {
    }
}
