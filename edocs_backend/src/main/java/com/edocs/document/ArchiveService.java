package com.edocs.document;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditLog;
import com.edocs.audit.AuditService;
import com.edocs.common.ApiException;
import com.edocs.common.TimeFormat;
import com.edocs.config.CacheConfig;
import com.edocs.document.DocumentDtos.ArchiveMetricDto;
import com.edocs.document.DocumentDtos.ArchiveRecordDto;
import com.edocs.document.DocumentDtos.DocumentDto;
import com.edocs.document.DocumentDtos.TimelineEntryDto;
import com.edocs.identity.OrganizationRepository;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;
import com.edocs.workflow.WorkflowService;

// WORM archive: search over sealed and in-flight documents, legal holds, and tamper metrics.
@Service
public class ArchiveService {

    private static final int MAX_RESULTS = 100;

    private final DocumentRepository documents;
    private final ContentStore contents;
    private final DocumentService documentService;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final OrganizationRepository organizations;

    public ArchiveService(DocumentRepository documents, ContentStore contents, DocumentService documentService, WorkflowService workflows,
            AuditService audit, OrganizationRepository organizations) {
        this.documents = documents;
        this.contents = contents;
        this.documentService = documentService;
        this.workflows = workflows;
        this.audit = audit;
        this.organizations = organizations;
    }

    public record Query(String text, String format, String hold, boolean sealedOnly) {
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> search(Query q) {
        AuthUser me = CurrentUser.get();
        Specification<Document> spec = (root, cq, cb) -> cb.equal(root.get("organization").get("id"), me.orgId());
        if (q.format() != null && !"all".equalsIgnoreCase(q.format())) {
            DocFormat format;
            try {
                format = DocFormat.valueOf(q.format().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("format must be all, pdf or docx.");
            }
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("format"), format));
        }
        if ("active".equalsIgnoreCase(q.hold()) || "released".equalsIgnoreCase(q.hold())) {
            boolean active = "active".equalsIgnoreCase(q.hold());
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("legalHold"), active));
        }
        if (q.sealedOnly()) {
            spec = spec.and((root, cq, cb) -> cb.isNotNull(root.get("sha256")));
        }
        // Metadata filters run in SQL; free-text terms are matched after decrypting bodies (content is encrypted at rest).
        List<String> terms = terms(q.text());
        List<Document> candidates = documents.findAll(spec).stream()
                .sorted(Comparator.comparing((Document d) -> d.getSha256() == null).thenComparing(Document::getUpdatedAt, Comparator.reverseOrder()))
                .toList();
        List<Document> hits = new ArrayList<>();
        for (Document d : candidates) {
            if (hits.size() >= MAX_RESULTS) {
                break;
            }
            if (terms.isEmpty() || matches(d, terms)) {
                hits.add(d);
            }
        }
        Map<UUID, Integer> steps = workflows.stepsOf(hits.stream().map(Document::getId).toList());
        return hits.stream().map(d -> DocumentMapper.summary(d, steps.getOrDefault(d.getId(), 1))).toList();
    }

    @Transactional(readOnly = true)
    public ArchiveRecordDto record(String id, DocumentAccess access) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        List<TimelineEntryDto> timeline = audit.forDocument(me.orgId(), doc.getId()).stream().map(ArchiveService::timeline).toList();
        return new ArchiveRecordDto(documentService.full(doc), timeline);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.ARCHIVE_METRICS, key = "#orgId")
    public List<ArchiveMetricDto> metrics(UUID orgId) {
        List<Document> all = documents.findAllInOrg(orgId);
        long sealed = all.stream().filter(d -> d.getSha256() != null).count();
        long objects = sealed + audit.count(orgId);
        AuditService.ChainReport chain = audit.verify(orgId);
        return List.of(
                new ArchiveMetricDto("Total WORM objects", String.format(Locale.US, "%,d", objects), null),
                new ArchiveMetricDto("Blockchain anchor lag", anchorLag(orgId), "seal"),
                new ArchiveMetricDto("Tamper events detected", chain.intact() ? "0" : "1", chain.intact() ? "seal" : null));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ARCHIVE_METRICS, allEntries = true)
    public void legalHold(String id, boolean on, DocumentAccess access) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        int years = organizations.findById(me.orgId()).map(o -> o.getRetentionYears()).orElse(7);
        doc.setLegalHold(on);
        doc.setHoldUntil(on ? LocalDate.now(ZoneOffset.UTC).plusYears(years) : null);
        documents.save(doc);
        audit.record(AuditKind.LEGAL_HOLD, on ? "Legal hold applied" : "Legal hold released", doc);
    }

    private boolean matches(Document d, List<String> terms) {
        String meta = (d.getId() + " " + d.getTitle() + " " + d.getCategory() + " " + d.getStatus()).toLowerCase(Locale.ROOT);
        if (terms.stream().allMatch(meta::contains)) {
            return true;
        }
        String hay = meta + " " + contents.plaintext(d).toLowerCase(Locale.ROOT);
        return terms.stream().allMatch(hay::contains);
    }

    // Splits "msa and indemnity" into terms; "field:value" keeps only the value.
    static List<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(t -> !t.isBlank() && !t.equals("and"))
                .map(t -> t.replaceFirst("^\\w+:", ""))
                .filter(t -> !t.isBlank())
                .toList();
    }

    // Average delay between a document's last signature and its anchor entry.
    private String anchorLag(UUID orgId) {
        Map<String, List<AuditLog>> sigs = audit.forKind(orgId, AuditKind.SIGNATURE).stream()
                .filter(a -> a.getEntityId() != null)
                .collect(Collectors.groupingBy(AuditLog::getEntityId));
        // Each anchor is measured from the last signature made before it, not from later re-signs.
        double avg = audit.forKind(orgId, AuditKind.ANCHOR).stream()
                .filter(a -> a.getEntityId() != null && sigs.containsKey(a.getEntityId()))
                .mapToLong(a -> sigs.get(a.getEntityId()).stream().map(AuditLog::getAt).filter(t -> !t.isAfter(a.getAt()))
                        .max(Instant::compareTo).map(t -> Duration.between(t, a.getAt()).toMillis()).orElse(-1L))
                .filter(ms -> ms >= 0)
                .average().orElse(0);
        return String.format(Locale.US, "%.1f s avg", avg / 1000.0);
    }

    private static TimelineEntryDto timeline(AuditLog a) {
        String kind = a.getKind() == AuditKind.ANCHOR ? "anchor" : a.getKind() == AuditKind.LEGAL_HOLD ? "legal-hold" : "signature";
        String ts = TimeFormat.iso(a.getAt()).replace('T', ' ').substring(0, 19) + " UTC";
        return new TimelineEntryDto(a.getId(), kind, a.getEvent(), ts, a.getActor() + " from " + a.getOrigin() + ". Hash " + a.getHash().substring(0, 16) + "…", a.getHash());
    }
}
