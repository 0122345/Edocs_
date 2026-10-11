package com.edocs.seed;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.edocs.compliance.ComplianceControl;
import com.edocs.compliance.ComplianceControlRepository;
import com.edocs.compliance.ComplianceService;
import com.edocs.document.DocumentService;
import com.edocs.document.Template;
import com.edocs.document.TemplateRepository;
import com.edocs.identity.Membership;
import com.edocs.identity.Organization;
import com.edocs.workflow.RoutingRule;
import com.edocs.workflow.RoutingRuleRepository;

// Gives a newly created workspace the starter templates, compliance controls and routing rules every screen expects.
@Component
public class WorkspaceProvisioner {

    private final TemplateRepository templates;
    private final ComplianceControlRepository controls;
    private final RoutingRuleRepository rules;

    public WorkspaceProvisioner(TemplateRepository templates, ComplianceControlRepository controls, RoutingRuleRepository rules) {
        this.templates = templates;
        this.controls = controls;
        this.rules = rules;
    }

    public void provision(Organization org, Membership owner) {
        templates.saveAll(List.of(
                new Template(org, "Master services agreement", "Commercial", SeedContent.MSA, List.of("client_name", "effective_date")),
                new Template(org, "Mutual NDA", "Confidentiality", SeedContent.NDA, List.of("term_years")),
                new Template(org, "Data processing addendum (GDPR)", "Privacy", SeedContent.DPA, List.of("processor_name", "controller_name")),
                new Template(org, "Enterprise SLA", "Commercial", SeedContent.SLA, List.of("uptime")),
                new Template(org, "Blank agreement", "General", DocumentService.BLANK_CONTENT, List.of())));
        saveRoutingRules(org);
        Instant now = Instant.now();
        record Control(String code, String name, String framework) {
        }
        for (Control c : List.of(
                new Control(ComplianceService.EIDAS, "eIDAS qualified signatures", "eIDAS"),
                new Control(ComplianceService.ESIGN, "ESIGN Act & UETA consent capture", "ESIGN / UETA"),
                new Control(ComplianceService.ENCRYPTION, "Encryption at rest (AES-256-GCM)", "SOC 2 CC6.1"),
                new Control(ComplianceService.RESIDENCY, "GDPR data residency (Frankfurt)", "GDPR Art. 44"),
                new Control(ComplianceService.RETENTION, "Retention schedule on archived contracts", "ISO 27001 A.5.33"),
                new Control(ComplianceService.ACCESS_REVIEW, "Access review for privileged roles", "SOC 2 CC6.2"))) {
            boolean pass = ComplianceService.evaluate(c.code(), org, List.of(), List.of(owner));
            controls.save(new ComplianceControl(org, c.code(), c.name(), c.framework(), pass ? ComplianceControl.Status.PASS : ComplianceControl.Status.WARN, now));
        }
    }

    void saveRoutingRules(Organization org) {
        rules.saveAll(List.of(
                new RoutingRule(org, "fast-track-nda", "Fast-track low-risk NDAs", "Skip legal review when the AI risk score is low and the template is unmodified.", false),
                new RoutingRule(org, "escalate-review", "Escalate stalled legal reviews", "Notify the legal lead when a review waits more than 2 days.", true),
                new RoutingRule(org, "parallel-kyc", "Run KYC in parallel", "Start identity checks for external signers while legal review is still open.", false),
                new RoutingRule(org, RoutingRule.ROUTE_HIGH_VALUE_TO_CFO, "Route high-value contracts to the CFO", "Add a CFO approval step when contract value exceeds $250,000.", true)));
    }
}
