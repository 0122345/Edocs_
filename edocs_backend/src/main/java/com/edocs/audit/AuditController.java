package com.edocs.audit;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.audit.AuditDtos.AuditLogDto;
import com.edocs.audit.AuditDtos.AuditPage;
import com.edocs.audit.AuditDtos.ChainVerification;
import com.edocs.document.DocStatus;
import com.edocs.document.DocumentRepository;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;
import com.edocs.security.Permission;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Audit")
@RestController
@RequestMapping("/audit-logs")
public class AuditController {

    private final AuditService audit;
    private final DocumentRepository documents;

    public AuditController(AuditService audit, DocumentRepository documents) {
        this.audit = audit;
        this.documents = documents;
    }

    // Auditors and admins see the whole trail; everyone else sees only their own actions.
    @GetMapping
    public AuditPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        AuthUser me = CurrentUser.get();
        int p = Math.max(0, page);
        int s = Math.clamp(size, 1, 200);
        Page<AuditLog> result = me.can(Permission.AUDIT_READ) ? audit.page(me.orgId(), p, s) : audit.pageForActor(me.orgId(), me.userId(), p, s);
        return new AuditPage(result.map(AuditLogDto::of).getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('audit:read')")
    public ChainVerification verify() {
        AuthUser me = CurrentUser.get();
        AuditService.ChainReport report = audit.verify(me.orgId());
        long sealed = documents.countByOrganizationIdAndStatus(me.orgId(), DocStatus.SIGNED)
                + documents.countByOrganizationIdAndStatus(me.orgId(), DocStatus.ARCHIVED);
        audit.record(AuditKind.ANCHOR, report.intact() ? "Hash chain verified" : "Hash chain verification FAILED", null,
                report.intact() ? AuditStatus.VERIFIED : AuditStatus.FAILED);
        return new ChainVerification(report.entries() + sealed, report.intact(), report.entries(), report.brokenAtSeq());
    }
}
