package com.edocs.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditService;
import com.edocs.audit.AuditStatus;
import com.edocs.common.ApiException;
import com.edocs.config.CacheConfig;
import com.edocs.document.DocumentContent.CommentEntry;
import com.edocs.document.DocumentDtos.CommentDto;
import com.edocs.document.DocumentDtos.CreateDocumentRequest;
import com.edocs.document.DocumentDtos.DocumentDto;
import com.edocs.document.DocumentDtos.PartyInput;
import com.edocs.identity.MembershipRepository;
import com.edocs.identity.OrganizationRepository;
import com.edocs.identity.Role;
import com.edocs.identity.User;
import com.edocs.identity.UserRepository;
import com.edocs.messaging.DomainEvent;
import com.edocs.messaging.MessagePublisher;
import com.edocs.notification.Channel;
import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.edocs.notification.NotificationType;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;
import com.edocs.workflow.RoutingRule;
import com.edocs.workflow.Workflow;
import com.edocs.workflow.WorkflowService;

@Service
public class DocumentService {

    public static final String BLANK_CONTENT = "<h2>1. Parties</h2><p>Describe the parties to this agreement.</p><h2>2. Terms</h2><p>Write the terms here.</p>";
    private static final BigDecimal CFO_THRESHOLD = new BigDecimal("250000");

    private final DocumentRepository documents;
    private final TemplateRepository templates;
    private final DocumentShareRepository shares;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final DocumentAccess access;
    private final ContentStore contents;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final NotificationService notifications;
    private final MessagePublisher publisher;

    public DocumentService(DocumentRepository documents, TemplateRepository templates, DocumentShareRepository shares, UserRepository users,
            OrganizationRepository organizations, MembershipRepository memberships, DocumentAccess access, ContentStore contents,
            WorkflowService workflows, AuditService audit, NotificationService notifications, MessagePublisher publisher) {
        this.documents = documents;
        this.templates = templates;
        this.shares = shares;
        this.users = users;
        this.organizations = organizations;
        this.memberships = memberships;
        this.access = access;
        this.contents = contents;
        this.workflows = workflows;
        this.audit = audit;
        this.notifications = notifications;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> list() {
        AuthUser me = CurrentUser.get();
        List<Document> docs = me.role() == Role.SIGNER ? documents.findVisibleTo(me.orgId(), me.email()) : documents.findAllInOrg(me.orgId());
        Map<UUID, Integer> steps = workflows.stepsOf(docs.stream().map(Document::getId).toList());
        return docs.stream().map(d -> DocumentMapper.summary(d, steps.getOrDefault(d.getId(), 1))).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDto get(String id) {
        Document doc = access.readable(id, CurrentUser.get());
        return full(doc);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TEMPLATES, CacheConfig.DASHBOARD}, allEntries = true)
    public DocumentDto create(CreateDocumentRequest req) {
        AuthUser me = CurrentUser.get();
        validateParties(req.parties());
        if (req.effectiveDate() != null && req.expiryDate() != null && req.expiryDate().isBefore(req.effectiveDate())) {
            throw ApiException.unprocessable("The expiry date must be on or after the effective date.");
        }
        User owner = users.getReferenceById(me.userId());
        Contract contract = new Contract(organizations.getReferenceById(me.orgId()), owner, req.title().trim(), req.category().trim());
        Template template = null;
        if (req.templateId() != null && !req.templateId().isBlank()) {
            template = templates.findInOrg(DocumentAccess.parse(req.templateId()), me.orgId())
                    .orElseThrow(() -> ApiException.notFound("That template no longer exists."));
            template.setUsageCount(template.getUsageCount() + 1);
            contract.setTemplate(template);
        }
        contract.setValue(req.value());
        contract.setEffectiveDate(req.effectiveDate());
        contract.setExpiryDate(req.expiryDate());
        req.parties().forEach(p -> contract.addParty(new Party(p.name().trim(), p.email(), p.role(), p.order())));
        contents.create(contract, template == null ? BLANK_CONTENT : template.getContent(), me.name(),
                template == null ? "Blank document" : "Created from " + template.getTitle(), Instant.now());
        documents.save(contract);
        workflows.createFor(contract);
        audit.record(AuditKind.CREATE, "Document created", contract);
        publisher.event(event(DomainEvent.DOCUMENT_CREATED, contract, me));
        return full(contract);
    }

    @Transactional
    public DocumentDto save(String id, String html, String note) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        if (doc.getStatus().isSealed()) {
            throw ApiException.conflict("Signed documents are sealed and cannot be edited.");
        }
        if (doc.getStatus() == DocStatus.OUT_FOR_SIGNATURE && hasAnySignature(doc)) {
            throw ApiException.conflict("Signing has started. Editing now would invalidate existing signatures.");
        }
        doc.setVersion(doc.getVersion() + 1);
        doc.setUpdatedAt(Instant.now());
        contents.saveVersion(doc, html, me.name(), note);
        documents.save(doc);
        audit.record(AuditKind.REDLINE, "Draft saved", doc);
        return full(doc);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void delete(String id) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        if (doc.getStatus() != DocStatus.DRAFT) {
            throw ApiException.conflict("Only drafts can be deleted. Signed and in-flight documents are retained for audit.");
        }
        audit.record(AuditKind.CREATE, "Draft deleted", doc);
        // Workflow, shares and OTP challenges are removed by ON DELETE CASCADE.
        documents.delete(doc);
        contents.delete(doc);
    }

    @Transactional(readOnly = true)
    public CommentDto addComment(String id, String body) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        CommentEntry c = contents.addComment(doc, me.userId().toString(), me.name(), initials(me.name()), body.trim());
        return new CommentDto(c.getId(), c.getAuthorName(), c.getInitials(), "Just now", c.getBody(), false);
    }

    @Transactional(readOnly = true)
    public void resolveComment(String id, String commentId) {
        Document doc = access.readable(id, CurrentUser.get());
        contents.resolveComment(doc, commentId);
    }

    @Transactional
    public void share(String id, String email, DocumentShare.Access level) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        String target = email.trim().toLowerCase();
        DocumentShare share = shares.findByDocumentAndEmail(doc.getId(), target)
                .orElseGet(() -> new DocumentShare(doc, target, level, users.getReferenceById(me.userId())));
        share.setAccess(level);
        shares.save(share);
        String verb = level.name().toLowerCase();
        notifications.send(me.orgId(), Recipient.user(me.userId(), me.email()), NotificationType.SHARE, Channel.IN_APP,
                "Shared with " + target, target + " can now " + verb + " “" + doc.getTitle() + "”. An email invitation is queued.", "/editor/" + id);
        notifications.send(me.orgId(), recipientFor(me.orgId(), target), NotificationType.SHARE, Channel.EMAIL,
                me.name() + " shared “" + doc.getTitle() + "” with you",
                me.name() + " gave you " + verb + " access to “" + doc.getTitle() + "” in Edocs.", "/editor/" + id);
        audit.record(AuditKind.SHARE, "Shared with " + target + " (" + verb + ")", doc);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DocumentDto send(String id) {
        AuthUser me = CurrentUser.get();
        Contract doc = access.readableContract(id, me);
        if (doc.getStatus().isSealed()) {
            throw ApiException.conflict("This document is already signed.");
        }
        List<Party> pending = doc.signers().stream().filter(p -> p.getSignedAt() == null).toList();
        if (doc.signers().isEmpty()) {
            throw ApiException.unprocessable("Add at least one signer before sending.");
        }
        doc.setStatus(DocStatus.OUT_FOR_SIGNATURE);
        doc.setUpdatedAt(Instant.now());
        Workflow w = workflows.of(doc);
        w.advanceTo(3);
        w.setTrigger(Workflow.TriggerType.ON_SEND);
        for (Party p : pending) {
            notifications.send(me.orgId(), recipientFor(me.orgId(), p.getEmail()), NotificationType.SIGNATURE_REQUEST, Channel.EMAIL,
                    "Signature requested", p.getName() + " (" + p.getEmail() + ") was asked to sign “" + doc.getTitle() + "”.", "/sign/" + id);
        }
        if (doc.getValue() != null && doc.getValue().compareTo(CFO_THRESHOLD) > 0 && workflows.ruleEnabled(me.orgId(), RoutingRule.ROUTE_HIGH_VALUE_TO_CFO)) {
            notifyAdmins(me, "CFO approval required", "“" + doc.getTitle() + "” exceeds $250,000 and needs CFO approval.", "/workflow/" + id);
        }
        audit.record(AuditKind.SIGNATURE, "Sent for signature to " + pending.size() + " signer" + (pending.size() == 1 ? "" : "s"), doc, AuditStatus.PENDING);
        publisher.event(event(DomainEvent.DOCUMENT_SENT, doc, me));
        return full(doc);
    }

    @Transactional
    public void remind(String id, String partyId) {
        AuthUser me = CurrentUser.get();
        Contract doc = access.readableContract(id, me);
        Party party = doc.getParties().stream().filter(p -> p.getId().toString().equals(partyId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("That party is not on this document."));
        if (party.getSignedAt() != null) {
            throw ApiException.conflict("This party has already signed.");
        }
        notifications.send(me.orgId(), recipientFor(me.orgId(), party.getEmail()), NotificationType.SIGNATURE_REQUEST, Channel.EMAIL,
                "Reminder: signature requested", party.getName() + " (" + party.getEmail() + ") was reminded to sign “" + doc.getTitle() + "”.", "/sign/" + id);
        audit.record(AuditKind.SHARE, "Signing reminder sent to " + party.getEmail(), doc);
    }

    public DocumentDto full(Document doc) {
        DocumentContent content = contents.load(doc);
        return DocumentMapper.full(doc, workflows.stepOf(doc), contents.plaintext(doc, content), content);
    }

    private void notifyAdmins(AuthUser me, String title, String body, String link) {
        memberships.findByOrgId(me.orgId()).stream()
                .filter(m -> m.isActive() && m.getUser().getRole() == Role.ADMIN)
                .forEach(m -> notifications.send(me.orgId(), Recipient.user(m.getUser().getId(), m.getUser().getEmail()),
                        NotificationType.SYSTEM, Channel.IN_APP, title, body, link));
    }

    // Members get the message in their bell as well as by email; outsiders only by email.
    private Recipient recipientFor(UUID orgId, String email) {
        return memberships.findByOrgIdAndEmail(orgId, email.toLowerCase())
                .map(m -> Recipient.user(m.getUser().getId(), email))
                .orElse(Recipient.external(email));
    }

    private boolean hasAnySignature(Document doc) {
        return doc instanceof Contract c && c.getParties().stream().anyMatch(p -> p.getSignedAt() != null);
    }

    private static void validateParties(List<PartyInput> parties) {
        Set<String> emails = new HashSet<>();
        for (PartyInput p : parties) {
            if (!emails.add(p.email().trim().toLowerCase())) {
                throw ApiException.unprocessable(p.email() + " is listed more than once.");
            }
        }
    }

    private static DomainEvent event(String type, Document doc, AuthUser me) {
        return new DomainEvent(type, me.orgId().toString(), doc.getId().toString(), me.userId().toString(), me.email(), Instant.now());
    }

    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return sb.isEmpty() ? "?" : sb.toString();
    }
}
