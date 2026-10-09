package com.edocs.workflow;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditDtos.AuditLogDto;
import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditLog;
import com.edocs.audit.AuditService;
import com.edocs.common.ApiException;
import com.edocs.common.TimeFormat;
import com.edocs.document.ContentStore;
import com.edocs.document.Contract;
import com.edocs.document.Document;
import com.edocs.document.DocumentAccess;
import com.edocs.document.DocumentContent;
import com.edocs.document.DocumentMapper;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;
import com.edocs.security.Permission;
import com.edocs.signing.SignatureRepository;
import com.edocs.workflow.WorkflowDtos.ActivityDto;
import com.edocs.workflow.WorkflowDtos.RoutingRuleDto;
import com.edocs.workflow.WorkflowDtos.StepDto;
import com.edocs.workflow.WorkflowDtos.WorkflowView;
import com.edocs.workflow.WorkflowStep.Completion;

@Service
public class WorkflowService {

    private final WorkflowRepository workflows;
    private final RoutingRuleRepository rules;
    private final DocumentAccess access;
    private final ContentStore contents;
    private final AuditService audit;
    private final SignatureRepository signatures;

    public WorkflowService(WorkflowRepository workflows, RoutingRuleRepository rules, DocumentAccess access, ContentStore contents,
            AuditService audit, SignatureRepository signatures) {
        this.workflows = workflows;
        this.rules = rules;
        this.access = access;
        this.contents = contents;
        this.audit = audit;
        this.signatures = signatures;
    }

    // Default four-stage contract workflow shown on the workflow screen.
    public Workflow createFor(Document doc) {
        Workflow w = new Workflow(doc, "Contract approval & signature");
        w.addStep(new WorkflowStep(1, "Draft", "Contract generated and clauses assembled.", Completion.COMPLETED));
        w.addStep(new WorkflowStep(2, "Legal review", "Internal compliance check and redlining.", Completion.APPROVED));
        w.addStep(new WorkflowStep(3, "Counterparty", "External negotiation and preliminary sign-off.", Completion.VERIFIED));
        w.addStep(new WorkflowStep(4, "QES / eIDAS", "Qualified electronic signature with biometric ID check.", Completion.VERIFIED));
        return workflows.save(w);
    }

    public Workflow of(Document doc) {
        return workflows.findByDocumentId(doc.getId()).orElseGet(() -> createFor(doc));
    }

    public int stepOf(Document doc) {
        return workflows.findByDocumentId(doc.getId()).map(Workflow::getCurrentStep).orElse(1);
    }

    public Map<UUID, Integer> stepsOf(Collection<UUID> docIds) {
        if (docIds.isEmpty()) {
            return Map.of();
        }
        return workflows.findByDocumentIds(docIds).stream()
                .collect(Collectors.toMap(w -> w.getDocument().getId(), Workflow::getCurrentStep));
    }

    public static List<StepDto> steps(Workflow w) {
        int current = w.getCurrentStep();
        return w.getSteps().stream().map(s -> new StepDto(s.getPosition(), s.getTitle(), s.getDescription(),
                s.getPosition() < current ? s.getCompletionStatus().name().toLowerCase()
                        : s.getPosition() == current ? "in-progress" : "waiting"))
                .toList();
    }

    @Transactional
    public WorkflowView view(String id) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        Workflow w = of(doc);
        DocumentContent content = contents.load(doc);
        List<ActivityDto> activity = audit.forDocument(me.orgId(), doc.getId()).stream().limit(3).map(WorkflowService::activity).toList();
        return new WorkflowView(DocumentMapper.full(doc, w.getCurrentStep(), contents.plaintext(doc, content), content), steps(w), activity);
    }

    // Court-ready evidence package: document fingerprint, parties, signatures, workflow and audit trail.
    @Transactional(readOnly = true)
    public Map<String, Object> evidence(String id) {
        AuthUser me = CurrentUser.get();
        Document doc = access.readable(id, me);
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("generatedAt", Instant.now());
        pkg.put("generatedBy", me.email());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", doc.getId());
        d.put("title", doc.getTitle());
        d.put("version", doc.getVersion());
        d.put("status", doc.getStatus());
        d.put("sha256", doc.getSha256());
        d.put("anchorTx", doc.getAnchorTx());
        d.put("legalHold", doc.isLegalHold());
        pkg.put("document", d);
        pkg.put("parties", doc instanceof Contract c ? c.getParties().stream().map(DocumentMapper::party).toList() : List.of());
        pkg.put("signatures", signatures.findByDocumentId(doc.getId()).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("signer", s.getParty().getEmail());
            m.put("level", s.getType());
            m.put("mode", s.getMode());
            m.put("signedAt", s.getSignedAt());
            m.put("ipAddress", s.getIpAddress());
            m.put("certificate", s.getCertificate());
            m.put("evidenceHash", s.getEvidenceHash());
            return m;
        }).toList());
        pkg.put("workflow", steps(of(doc)));
        pkg.put("auditTrail", audit.forDocument(me.orgId(), doc.getId()).stream().map(AuditLogDto::of).toList());
        pkg.put("auditChainIntact", audit.verify(me.orgId()).intact());
        return pkg;
    }

    @Transactional(readOnly = true)
    public List<RoutingRuleDto> rules() {
        return rules.findAllInOrg(CurrentUser.get().orgId()).stream().map(WorkflowService::rule).toList();
    }

    @Transactional
    public List<RoutingRuleDto> saveRules(List<RoutingRuleDto> input) {
        AuthUser me = CurrentUser.get();
        CurrentUser.require(Permission.WORKFLOW_MANAGE);
        Map<String, RoutingRule> byId = rules.findAllInOrg(me.orgId()).stream()
                .collect(Collectors.toMap(r -> r.getId().toString(), Function.identity()));
        for (RoutingRuleDto dto : input) {
            RoutingRule rule = byId.get(dto.id());
            if (rule == null) {
                throw ApiException.notFound("Routing rule " + dto.id() + " does not exist.");
            }
            rule.setEnabled(dto.enabled());
        }
        long active = byId.values().stream().filter(RoutingRule::isEnabled).count();
        audit.record(AuditKind.REDLINE, "Routing rules updated (" + active + " active)", null);
        return byId.values().stream().sorted(java.util.Comparator.comparing(RoutingRule::getCode)).map(WorkflowService::rule).toList();
    }

    public boolean ruleEnabled(UUID orgId, String code) {
        return rules.findAllInOrg(orgId).stream().anyMatch(r -> r.getCode().equals(code) && r.isEnabled());
    }

    private static RoutingRuleDto rule(RoutingRule r) {
        return new RoutingRuleDto(r.getId().toString(), r.getLabel(), r.getDescription(), r.isEnabled());
    }

    private static ActivityDto activity(AuditLog a) {
        String icon = switch (a.getKind()) {
            case SIGNATURE -> "identity";
            case ANCHOR, LEGAL_HOLD -> "lock";
            default -> "edit";
        };
        return new ActivityDto(a.getId(), icon, a.getEvent() + " by " + a.getActor() + ".", TimeFormat.stamp(a.getAt()) + " · " + a.getOrigin());
    }
}
