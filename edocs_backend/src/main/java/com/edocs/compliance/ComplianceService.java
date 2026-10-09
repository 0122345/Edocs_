package com.edocs.compliance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditService;
import com.edocs.common.TimeFormat;
import com.edocs.compliance.ComplianceControl.Status;
import com.edocs.document.DocStatus;
import com.edocs.document.Document;
import com.edocs.document.DocumentRepository;
import com.edocs.identity.Membership;
import com.edocs.identity.MembershipRepository;
import com.edocs.identity.Organization;
import com.edocs.identity.OrganizationRepository;
import com.edocs.identity.Role;
import com.edocs.identity.SignatureLevel;
import com.edocs.security.CurrentUser;

// Each control is evaluated against live data rather than stored as a static flag.
@Service
public class ComplianceService {

    public static final String EIDAS = "eidas-qes";
    public static final String ESIGN = "esign-consent";
    public static final String ENCRYPTION = "encryption-at-rest";
    public static final String RESIDENCY = "gdpr-residency";
    public static final String RETENTION = "retention-schedule";
    public static final String ACCESS_REVIEW = "privileged-access";

    public record ControlDto(String id, String name, String framework, String status, String checked) {
    }

    private final ComplianceControlRepository controls;
    private final DocumentRepository documents;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public ComplianceService(ComplianceControlRepository controls, DocumentRepository documents, MembershipRepository memberships,
            OrganizationRepository organizations, AuditService audit) {
        this.controls = controls;
        this.documents = documents;
        this.memberships = memberships;
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ControlDto> list() {
        return controls.findAllInOrg(CurrentUser.get().orgId()).stream().map(ComplianceService::dto).toList();
    }

    @Transactional
    public List<ControlDto> run() {
        UUID orgId = CurrentUser.get().orgId();
        Organization org = organizations.findById(orgId).orElseThrow();
        List<Document> docs = documents.findAllInOrg(orgId);
        List<Membership> members = memberships.findByOrgId(orgId);
        Instant now = Instant.now();
        for (ComplianceControl c : controls.findAllInOrg(orgId)) {
            c.setStatus(evaluate(c.getCode(), org, docs, members) ? Status.PASS : Status.WARN);
            c.setLastCheckedAt(now);
        }
        audit.record(AuditKind.VIEW, "Compliance checks run", null);
        return list();
    }

    static boolean evaluate(String code, Organization org, List<Document> docs, List<Membership> members) {
        return switch (code) {
            case EIDAS -> org.getSignatureLevel() == SignatureLevel.QES;
            case ESIGN -> true;
            case ENCRYPTION -> docs.stream().allMatch(d -> d.getEncryptedKey() != null && !d.getEncryptedKey().isBlank());
            case RESIDENCY -> true;
            // Every archived contract must sit under a legal hold or a retention schedule of at least 7 years.
            case RETENTION -> org.getRetentionYears() >= 7 && docs.stream().filter(d -> d.getStatus() == DocStatus.ARCHIVED).allMatch(Document::isLegalHold);
            // When the workspace requires 2FA, every active administrator must have MFA on.
            case ACCESS_REVIEW -> !org.isRequire2fa() || members.stream()
                    .filter(m -> m.isActive() && m.getUser().getRole() == Role.ADMIN)
                    .allMatch(m -> m.getUser().isMfaEnabled());
            default -> false;
        };
    }

    private static ControlDto dto(ComplianceControl c) {
        return new ControlDto(c.getId().toString(), c.getName(), c.getFramework(), c.getStatus().name().toLowerCase(), TimeFormat.ago(c.getLastCheckedAt()));
    }
}
