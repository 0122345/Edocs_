package com.edocs.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.edocs.common.ClientInfo;
import com.edocs.common.Hashing;
import com.edocs.document.Document;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;

@Service
public class AuditService {

    public static final String GENESIS = "0".repeat(64);
    private static final int MAX_APPEND_RETRIES = 5;

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public record Actor(String orgId, String actorId, String actor, String origin) {

        public static Actor of(AuthUser user) {
            return new Actor(user.orgId().toString(), user.userId().toString(), user.email(), ClientInfo.ip());
        }

        public static Actor system(UUID orgId, String name, String origin) {
            return new Actor(orgId.toString(), null, name, origin);
        }
    }

    public record ChainReport(long entries, boolean intact, Long brokenAtSeq) {
    }

    // Records an action by the signed-in user.
    public AuditLog record(AuditKind kind, String event, Document doc, AuditStatus status) {
        return append(Actor.of(CurrentUser.get()), kind, event, doc == null ? null : doc.getId().toString(),
                doc == null ? "Workspace" : fileName(doc), status, Instant.now());
    }

    public AuditLog record(AuditKind kind, String event, Document doc) {
        return record(kind, event, doc, AuditStatus.VERIFIED);
    }

    // Appends with optimistic sequencing: a concurrent writer that takes the same seq loses and retries on the new head.
    public AuditLog append(Actor actor, AuditKind kind, String event, String entityId, String documentName, AuditStatus status, Instant at) {
        Instant when = at.truncatedTo(ChronoUnit.MILLIS);
        for (int attempt = 0; attempt < MAX_APPEND_RETRIES; attempt++) {
            AuditLog head = repo.findTopByOrgIdOrderBySeqDesc(actor.orgId()).orElse(null);
            long seq = head == null ? 1 : head.getSeq() + 1;
            String prev = head == null ? GENESIS : head.getHash();
            AuditLog entry = AuditLog.builder()
                    .orgId(actor.orgId()).seq(seq).kind(kind).event(event)
                    .entityType(entityId == null ? "Workspace" : "Document").entityId(entityId).documentName(documentName)
                    .actorId(actor.actorId()).actor(actor.actor()).origin(actor.origin())
                    .at(when).status(status).prevHash(prev)
                    .build();
            entry = withHash(entry);
            try {
                return repo.insert(entry);
            } catch (DuplicateKeyException race) {
                // Another entry claimed this seq; re-read the head and try again.
            }
        }
        throw new IllegalStateException("Could not append audit entry after " + MAX_APPEND_RETRIES + " attempts");
    }

    public Page<AuditLog> page(UUID orgId, int page, int size) {
        return repo.findByOrgIdOrderByAtDesc(orgId.toString(), PageRequest.of(page, size));
    }

    public Page<AuditLog> pageForActor(UUID orgId, UUID actorId, int page, int size) {
        return repo.findByOrgIdAndActorIdOrderByAtDesc(orgId.toString(), actorId.toString(), PageRequest.of(page, size));
    }

    public List<AuditLog> forDocument(UUID orgId, UUID documentId) {
        return repo.findByOrgIdAndEntityIdOrderByAtDesc(orgId.toString(), documentId.toString());
    }

    public List<AuditLog> forKind(UUID orgId, AuditKind kind) {
        return repo.findByOrgIdAndKind(orgId.toString(), kind);
    }

    public long count(UUID orgId) {
        return repo.countByOrgId(orgId.toString());
    }

    // Walks the whole chain in seq order and recomputes every hash.
    public ChainReport verify(UUID orgId) {
        long n = 0;
        String expectedPrev = GENESIS;
        try (Stream<AuditLog> all = repo.findByOrgIdOrderBySeqAsc(orgId.toString())) {
            Iterator<AuditLog> it = all.iterator();
            while (it.hasNext()) {
                AuditLog e = it.next();
                n++;
                if (!expectedPrev.equals(e.getPrevHash()) || !computeHash(e).equals(e.getHash())) {
                    return new ChainReport(n, false, e.getSeq());
                }
                expectedPrev = e.getHash();
            }
        }
        return new ChainReport(n, true, null);
    }

    public static String fileName(Document doc) {
        String base = doc.getTitle().replaceAll("[^\\w]+", "-").replaceAll("^-|-$", "");
        return base + "." + doc.getFormat().name().toLowerCase();
    }

    static String computeHash(AuditLog e) {
        return Hashing.sha256Hex(e.getPrevHash(), e.getOrgId(), Long.toString(e.getSeq()), e.getKind().name(), e.getEvent(),
                e.getEntityId(), e.getActor(), e.getOrigin(), e.getAt().toString(), e.getStatus().name());
    }

    private static AuditLog withHash(AuditLog e) {
        return e.toBuilder().hash(computeHash(e)).build();
    }
}
