package com.edocs.seed;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditService;
import com.edocs.audit.AuditService.Actor;
import com.edocs.audit.AuditStatus;
import com.edocs.common.Hashing;
import com.edocs.common.crypto.EnvelopeCrypto;
import com.edocs.compliance.ComplianceControl;
import com.edocs.compliance.ComplianceControlRepository;
import com.edocs.compliance.ComplianceService;
import com.edocs.config.EdocsProperties;
import com.edocs.document.Contract;
import com.edocs.document.DocFormat;
import com.edocs.document.DocStatus;
import com.edocs.document.DocumentContent;
import com.edocs.document.DocumentContent.CommentEntry;
import com.edocs.document.DocumentContent.VersionSnapshot;
import com.edocs.document.DocumentContentRepository;
import com.edocs.document.DocumentRepository;
import com.edocs.document.Party;
import com.edocs.document.PartyRole;
import com.edocs.document.Template;
import com.edocs.document.TemplateRepository;
import com.edocs.identity.KycStatus;
import com.edocs.identity.Membership;
import com.edocs.identity.MembershipRepository;
import com.edocs.identity.Organization;
import com.edocs.identity.OrganizationRepository;
import com.edocs.identity.Role;
import com.edocs.identity.SignatureLevel;
import com.edocs.identity.User;
import com.edocs.identity.UserRepository;
import com.edocs.notification.Channel;
import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.edocs.notification.NotificationType;
import com.edocs.signing.Signature;
import com.edocs.signing.SignatureRepository;
import com.edocs.workflow.RoutingRule;
import com.edocs.workflow.RoutingRuleRepository;
import com.edocs.workflow.Workflow;
import com.edocs.workflow.WorkflowService;

// Loads the same demo workspace the frontend mock uses, once, into an empty database.
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final EdocsProperties props;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final TemplateRepository templates;
    private final DocumentRepository documents;
    private final DocumentContentRepository contents;
    private final SignatureRepository signatures;
    private final RoutingRuleRepository rules;
    private final ComplianceControlRepository controls;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final NotificationService notifications;
    private final EnvelopeCrypto crypto;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(EdocsProperties props, OrganizationRepository organizations, UserRepository users, MembershipRepository memberships,
            TemplateRepository templates, DocumentRepository documents, DocumentContentRepository contents, SignatureRepository signatures,
            RoutingRuleRepository rules, ComplianceControlRepository controls, WorkflowService workflows, AuditService audit,
            NotificationService notifications, EnvelopeCrypto crypto, PasswordEncoder encoder) {
        this.props = props;
        this.organizations = organizations;
        this.users = users;
        this.memberships = memberships;
        this.templates = templates;
        this.documents = documents;
        this.contents = contents;
        this.signatures = signatures;
        this.rules = rules;
        this.controls = controls;
        this.workflows = workflows;
        this.audit = audit;
        this.notifications = notifications;
        this.crypto = crypto;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || organizations.count() > 0) {
            return;
        }
        log.info("Seeding demo workspace (password for every demo account: {})", props.seed().demoPassword());

        Organization org = organizations.save(new Organization("Acme Corporation", "acme.corp", "j.davis@acme.corp"));
        org.setSignatureLevel(SignatureLevel.QES);

        User admin = user(org, "Jordan Davis", "j.davis@acme.corp", Role.ADMIN, KycStatus.VERIFIED, true, "2025-01-12");
        User legal = user(org, "Sarah Jenkins", "s.jenkins@acme.corp", Role.LEGAL, KycStatus.VERIFIED, false, "2025-03-02");
        User signer = user(org, "Marcus Vance", "m.vance@partnercorp.io", Role.SIGNER, KycStatus.VERIFIED, false, "2026-06-18");
        user(org, "Elena Rostova", "e.rostova@acme.corp", Role.AUDITOR, KycStatus.PENDING, false, "2025-09-30");

        Template msa = template(org, "Master services agreement", "Commercial", SeedContent.MSA, List.of("client_name", "effective_date"), 412, "2026-09-28T09:00:00Z");
        Template nda = template(org, "Mutual NDA", "Confidentiality", SeedContent.NDA, List.of("term_years"), 1033, "2026-09-12T09:00:00Z");
        Template dpa = template(org, "Data processing addendum (GDPR)", "Privacy", SeedContent.DPA, List.of("processor_name", "controller_name"), 288, "2026-08-30T09:00:00Z");
        Template sla = template(org, "Enterprise SLA", "Commercial", SeedContent.SLA, List.of("uptime"), 167, "2026-08-02T09:00:00Z");
        template(org, "Reseller addendum", "Partnerships", com.edocs.document.DocumentService.BLANK_CONTENT, List.of(), 74, "2026-07-19T09:00:00Z");
        template(org, "Employment offer letter", "HR", com.edocs.document.DocumentService.BLANK_CONTENT, List.of("candidate_name", "start_date", "salary"), 539, "2026-07-03T09:00:00Z");

        Contract q3 = contract(org, legal, "Q3 Master Services Agreement", "Commercial", msa, DocFormat.PDF, DocStatus.OUT_FOR_SIGNATURE, 3,
                "2026-09-01T09:00:00Z", "2026-10-02T14:25:00Z", 4300, 34);
        q3.setValue(new BigDecimal("480000"));
        q3.setEffectiveDate(LocalDate.parse("2026-10-24"));
        q3.setExpiryDate(LocalDate.parse("2029-10-23"));
        q3.setLegalHold(true);
        q3.setHoldUntil(LocalDate.parse("2031-10-14"));
        party(q3, "Jordan Davis", "j.davis@acme.corp", 1, "2026-09-14T08:30:00Z");
        party(q3, "Marcus Vance", "m.vance@partnercorp.io", 2, null);
        body(q3, SeedContent.MSA,
                List.of(version(1, "Version 1.0 (initial draft)", "Created from MSA template", "E. Rostova", "2026-09-01T09:00:00Z"),
                        version(2, "Version 1.1", "Accepted HA clause redline", "M. Vance", "2026-10-01T09:12:00Z"),
                        version(3, "Version 1.2", "Added indemnification cap", "S. Jenkins", "2026-10-02T14:25:00Z")),
                List.of(comment(legal, "We need to make sure the indemnity cap lines up with the standard SaaS SLAs in section 3.", "2026-10-02T14:15:00Z"),
                        comment(signer, "Agreed on the updated high-availability clause. Looks solid.", "2026-10-02T13:25:00Z")));

        String ndaBody = SeedContent.NDA.replace("{{term_years}}", "3");
        Contract vendorNda = contract(org, legal, "Vendor NDA 2026", "Confidentiality", nda, DocFormat.PDF, DocStatus.SIGNED, 1,
                "2026-09-18T09:00:00Z", "2026-09-21T16:40:00Z", 820, 6);
        party(vendorNda, "Jordan Davis", "j.davis@acme.corp", 1, "2026-09-20T10:00:00Z");
        party(vendorNda, "CFO, PartnerCorp", "cfo@partnercorp.io", 2, "2026-09-21T16:40:00Z");
        seal(vendorNda, ndaBody);
        body(vendorNda, ndaBody, List.of(version(1, "Version 1.0", null, "S. Jenkins", "2026-09-18T09:00:00Z")), List.of());

        String slaBody = SeedContent.SLA.replace("{{uptime}}", "99.95%");
        Contract slaDoc = contract(org, legal, "Enterprise SLA v2", "Commercial", sla, DocFormat.DOCX, DocStatus.IN_REVIEW, 2,
                "2026-09-25T09:00:00Z", "2026-10-02T12:05:00Z", 210, 9);
        slaDoc.setValue(new BigDecimal("120000"));
        slaDoc.getParties().add(partyOf(slaDoc, "Legal, Globex", "legal@globex.org", PartyRole.APPROVER, 1));
        body(slaDoc, slaBody, List.of(version(1, "Version 1.0", "Created from Enterprise SLA", "S. Jenkins", "2026-09-25T09:00:00Z"),
                version(2, "Version 1.1", "Clause redline", "Legal, Globex", "2026-10-02T12:05:00Z")), List.of());

        String dpaBody = SeedContent.DPA.replace("{{processor_name}}", "Acme Corporation").replace("{{controller_name}}", "Initech");
        Contract dpaDoc = contract(org, admin, "Data Processing Addendum – Initech", "Privacy", dpa, DocFormat.DOCX, DocStatus.DRAFT, 1,
                "2026-10-01T09:00:00Z", "2026-10-01T09:00:00Z", 95, 4);
        party(dpaDoc, "Ops, Initech", "ops@initech.example", 1, null);
        body(dpaDoc, dpaBody, List.of(version(1, "Version 1.0 (initial draft)", null, "J. Davis", "2026-10-01T09:00:00Z")), List.of());

        Contract supplier = contract(org, admin, "Supplier Terms 2025", "Commercial", null, DocFormat.PDF, DocStatus.ARCHIVED, 1,
                "2025-01-20T09:00:00Z", "2025-02-10T11:00:00Z", 1600, 18);
        supplier.setLegalHold(true);
        supplier.setHoldUntil(LocalDate.parse("2032-02-10"));
        party(supplier, "Global Logistics Partner", "contracts@glp.example", 1, "2025-02-10T11:00:00Z");
        seal(supplier, SeedContent.SUPPLY);
        body(supplier, SeedContent.SUPPLY, List.of(version(1, "Version 1.0", null, "J. Davis", "2025-02-10T11:00:00Z")), List.of());

        List<Contract> all = List.of(q3, vendorNda, slaDoc, dpaDoc, supplier);
        documents.saveAll(all);
        documents.flush();
        Map<String, Integer> steps = Map.of(q3.getTitle(), 4, vendorNda.getTitle(), 5, slaDoc.getTitle(), 2, dpaDoc.getTitle(), 1, supplier.getTitle(), 5);
        for (Contract c : all) {
            workflows.createFor(c).advanceTo(steps.get(c.getTitle()));
            signatureRows(c, admin);
        }

        rules.save(new RoutingRule(org, "fast-track-nda", "Fast-track low-risk NDAs", "Skip legal review when the AI risk score is low and the template is unmodified.", false));
        rules.save(new RoutingRule(org, "escalate-review", "Escalate stalled legal reviews", "Notify the legal lead when a review waits more than 2 days.", true));
        rules.save(new RoutingRule(org, "parallel-kyc", "Run KYC in parallel", "Start identity checks for external signers while legal review is still open.", false));
        rules.save(new RoutingRule(org, RoutingRule.ROUTE_HIGH_VALUE_TO_CFO, "Route high-value contracts to the CFO", "Add a CFO approval step when contract value exceeds $250,000.", true));

        Instant now = Instant.now();
        control(org, ComplianceService.EIDAS, "eIDAS qualified signatures", "eIDAS", ComplianceControl.Status.PASS, now.minusSeconds(7200));
        control(org, ComplianceService.ESIGN, "ESIGN Act & UETA consent capture", "ESIGN / UETA", ComplianceControl.Status.PASS, now.minusSeconds(7200));
        control(org, ComplianceService.ENCRYPTION, "Encryption at rest (AES-256-GCM)", "SOC 2 CC6.1", ComplianceControl.Status.PASS, now.minusSeconds(14400));
        control(org, ComplianceService.RESIDENCY, "GDPR data residency (Frankfurt)", "GDPR Art. 44", ComplianceControl.Status.PASS, now.minusSeconds(14400));
        control(org, ComplianceService.RETENTION, "Retention schedule on archived contracts", "ISO 27001 A.5.33", ComplianceControl.Status.WARN, now.minusSeconds(86400));
        control(org, ComplianceService.ACCESS_REVIEW, "Access review for privileged roles", "SOC 2 CC6.2", ComplianceControl.Status.WARN, now.minusSeconds(3 * 86400));

        seedAudit(org, q3, vendorNda, slaDoc, supplier);

        for (User u : List.of(admin, legal)) {
            notifications.send(org.getId(), Recipient.user(u.getId(), u.getEmail()), NotificationType.SIGNATURE_REQUEST, Channel.IN_APP, "Signature requested",
                    "Marcus Vance was asked to sign Q3 Master Services Agreement.", "/workflow/" + q3.getId());
            notifications.send(org.getId(), Recipient.user(u.getId(), u.getEmail()), NotificationType.SYSTEM, Channel.IN_APP, "Legal review is stalling",
                    "Enterprise SLA v2 has waited 3 days for legal review.", "/editor/" + slaDoc.getId());
        }
        notifications.send(org.getId(), Recipient.user(signer.getId(), signer.getEmail()), NotificationType.SIGNATURE_REQUEST, Channel.IN_APP,
                "Signature requested", "You were asked to sign Q3 Master Services Agreement.", "/sign/" + q3.getId());
        log.info("Demo workspace ready: {} users, {} documents", users.count(), documents.count());
    }

    private User user(Organization org, String name, String email, Role role, KycStatus kyc, boolean mfa, String joined) {
        User u = new User(name, email, role);
        u.setKycStatus(kyc);
        u.setMfaEnabled(mfa);
        u.setPasswordHash(encoder.encode(props.seed().demoPassword()));
        users.save(u);
        memberships.save(new Membership(u, org, LocalDate.parse(joined)));
        return u;
    }

    private Template template(Organization org, String name, String category, String content, List<String> vars, int uses, String updated) {
        Template t = new Template(org, name, category, content, vars);
        t.setUsageCount(uses);
        t.setCreatedAt(Instant.parse(updated));
        t.setUpdatedAt(Instant.parse(updated));
        return templates.save(t);
    }

    private Contract contract(Organization org, User owner, String title, String category, Template template, DocFormat format, DocStatus status,
            int version, String created, String updated, int sizeKb, int pages) {
        Contract c = new Contract(org, owner, title, category);
        c.setTemplate(template);
        c.setFormat(format);
        c.setStatus(status);
        c.setVersion(version);
        c.setCreatedAt(Instant.parse(created));
        c.setUpdatedAt(Instant.parse(updated));
        c.setSizeKb(sizeKb);
        c.setPages(pages);
        return c;
    }

    private void party(Contract c, String name, String email, int order, String signedAt) {
        Party p = partyOf(c, name, email, PartyRole.SIGNER, order);
        p.setSignedAt(signedAt == null ? null : Instant.parse(signedAt));
        c.getParties().add(p);
    }

    private static Party partyOf(Contract c, String name, String email, PartyRole role, int order) {
        Party p = new Party(name, email, role, order);
        p.setContract(c);
        return p;
    }

    private void seal(Contract c, String body) {
        c.setSha256(Hashing.sha256Hex(body, c.getTitle()));
        c.setAnchorTx("0x" + Hashing.sha256Hex("anchor", c.getTitle()));
    }

    // Encrypts the body and every version snapshot with a fresh per-document data key.
    private void body(Contract c, String html, List<VersionSnapshot> versions, List<CommentEntry> comments) {
        SecretKey key = crypto.newDataKey();
        c.setEncryptedKey(crypto.wrap(key));
        DocumentContent content = new DocumentContent(c.getId().toString(), c.getOrganization().getId().toString(), crypto.encrypt(html, key));
        versions.forEach(v -> v.setCiphertext(crypto.encrypt(html, key)));
        content.setVersions(new ArrayList<>(versions));
        content.setComments(new ArrayList<>(comments));
        contents.save(content);
    }

    private static VersionSnapshot version(int n, String label, String note, String by, String at) {
        return new VersionSnapshot(UUID.randomUUID().toString(), n, label, note, by, Instant.parse(at), null);
    }

    private static CommentEntry comment(User author, String body, String at) {
        return new CommentEntry(UUID.randomUUID().toString(), author.getId().toString(), author.getFullName(), author.initials(), body, Instant.parse(at), false);
    }

    private void signatureRows(Contract c, User admin) {
        for (Party p : c.getParties()) {
            if (p.getSignedAt() == null) {
                continue;
            }
            Signature s = new Signature();
            s.setId(UUID.randomUUID());
            s.setDocument(c);
            s.setParty(p);
            s.setSigner(p.getEmail().equals(admin.getEmail()) ? admin : null);
            s.setType(SignatureLevel.QES);
            s.setMode("draw");
            s.setSignedAt(p.getSignedAt());
            s.setIpAddress("192.168.1.45");
            s.setEvidenceHash(Hashing.sha256Hex(c.getId().toString(), p.getEmail(), p.getSignedAt().toString()));
            s.setCertificate("CN=" + p.getName() + ", E=" + p.getEmail() + "; level=QES; issuer=Edocs Development Trust Service (simulated QTSP)");
            signatures.save(s);
        }
    }

    private void control(Organization org, String code, String name, String framework, ComplianceControl.Status status, Instant checked) {
        controls.save(new ComplianceControl(org, code, name, framework, status, checked));
    }

    private record SeedAudit(AuditKind kind, String event, Contract doc, String docName, String actor, String origin, String at, AuditStatus status) {
    }

    private void seedAudit(Organization org, Contract q3, Contract nda, Contract sla, Contract supplier) {
        List<SeedAudit> entries = new ArrayList<>(List.of(
                new SeedAudit(AuditKind.SIGNATURE, "E-signature executed", q3, null, "j.davis@acme.corp", "192.168.1.45", "2026-10-02T14:22:00Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.REDLINE, "Clause redline modified", sla, null, "legal@globex.org", "10.0.4.112", "2026-10-02T12:05:00Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.VIEW, "Document viewed", nda, null, "cfo@partnercorp.io", "172.16.8.2", "2026-10-01T19:40:00Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.KEY_ROTATION, "Encryption key rotated", null, "System-Vault-Master", "system-daemon", "Internal KMS", "2026-09-24T04:00:00Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.SIGNATURE, "E-signature executed", nda, null, "cfo@partnercorp.io", "172.16.8.9", "2026-09-21T16:40:00Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.ANCHOR, "Merkle root anchored", q3, null, "anchor-service", "Ethereum Mainnet (simulated)", "2026-09-14T08:42:19Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.LEGAL_HOLD, "Legal hold applied", q3, null, "j.davis@acme.corp", "192.168.1.12", "2026-09-14T08:41:00Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.SIGNATURE, "Multi-party QES signature executed", q3, null, "j.davis@acme.corp", "Qualified TSP", "2026-09-14T08:35:12Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.VIEW, "Document viewed", sla, null, "auditor@kpmg.example", "81.20.4.19", "2026-09-10T15:02:00Z", AuditStatus.VERIFIED),
                new SeedAudit(AuditKind.SIGNATURE, "E-signature rejected", null, "Supplier-Terms-2026.pdf", "ops@initech.example", "10.2.0.31", "2026-09-09T09:58:00Z", AuditStatus.FAILED),
                new SeedAudit(AuditKind.LEGAL_HOLD, "Legal hold applied", supplier, null, "j.davis@acme.corp", "192.168.1.45", "2026-09-02T10:00:00Z", AuditStatus.PENDING),
                new SeedAudit(AuditKind.ANCHOR, "Merkle root anchored", null, "Batch-2026-08-31", "anchor-service", "Ethereum Mainnet (simulated)", "2026-08-31T08:40:00Z", AuditStatus.VERIFIED)));
        entries.sort(Comparator.comparing(SeedAudit::at));
        for (SeedAudit e : entries) {
            audit.append(new Actor(org.getId().toString(), null, e.actor(), e.origin()), e.kind(), e.event(),
                    e.doc() == null ? null : e.doc().getId().toString(), e.doc() == null ? e.docName() : AuditService.fileName(e.doc()),
                    e.status(), Instant.parse(e.at()));
        }
    }
}
