package com.edocs.dashboard;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.compliance.ComplianceControl;
import com.edocs.compliance.ComplianceControlRepository;
import com.edocs.config.CacheConfig;
import com.edocs.document.Contract;
import com.edocs.document.DocStatus;
import com.edocs.document.Document;
import com.edocs.document.DocumentRepository;
import com.edocs.document.Party;
import com.edocs.identity.KycStatus;
import com.edocs.identity.MembershipRepository;
import com.fasterxml.jackson.annotation.JsonInclude;

// KPIs computed from live data and cached for 30 seconds per organization.
@Service
public class DashboardService {

    private static final BigDecimal HIGH_VALUE = new BigDecimal("250000");

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Kpi(String id, String label, String value, String unit, String delta, String deltaTone, String footnote) {
    }

    public record RiskBand(String level, String label, int percent, long docs) {
    }

    public record Bottleneck(String stage, double avgDays, String note) {
    }

    public record Dashboard(List<Kpi> kpis, Map<String, List<RiskBand>> riskBands, List<Bottleneck> bottlenecks) {
    }

    private final DocumentRepository documents;
    private final ComplianceControlRepository controls;
    private final MembershipRepository memberships;

    public DashboardService(DocumentRepository documents, ComplianceControlRepository controls, MembershipRepository memberships) {
        this.documents = documents;
        this.controls = controls;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "#orgId")
    public Dashboard build(UUID orgId) {
        Instant now = Instant.now();
        List<Document> docs = documents.findAllInOrg(orgId);
        List<Contract> signed = docs.stream().filter(d -> d.getStatus().isSealed()).filter(Contract.class::isInstance).map(Contract.class::cast).toList();

        double velocity = signed.stream().mapToDouble(c -> days(c.getCreatedAt(), lastSignature(c))).average().orElse(0);
        long pending = docs.stream().filter(d -> d.getStatus() == DocStatus.OUT_FOR_SIGNATURE).count();
        long urgent = docs.stream().filter(d -> d.getStatus() == DocStatus.OUT_FOR_SIGNATURE && d.getUpdatedAt().isBefore(now.minus(Duration.ofDays(3)))).count();
        List<ComplianceControl> cs = controls.findAllInOrg(orgId);
        long passing = cs.stream().filter(c -> c.getStatus() == ComplianceControl.Status.PASS).count();
        double posture = cs.isEmpty() ? 100 : 100.0 * passing / cs.size();
        long thisMonth = signed.stream().filter(c -> lastSignature(c).isAfter(now.minus(Duration.ofDays(30)))).count();
        long lastMonth = signed.stream().filter(c -> lastSignature(c).isAfter(now.minus(Duration.ofDays(60))) && !lastSignature(c).isAfter(now.minus(Duration.ofDays(30)))).count();
        String growth = lastMonth == 0 ? (thisMonth > 0 ? "New this month" : "No change") : String.format(Locale.US, "%+.1f%% vs last month", 100.0 * (thisMonth - lastMonth) / lastMonth);

        List<Kpi> kpis = List.of(
                new Kpi("velocity", "Contract velocity", String.format(Locale.US, "%.1f", velocity), "days", signed.size() + " contracts completed", "seal", "Average time from creation to final signature"),
                new Kpi("pending", "Pending signatures", Long.toString(pending), null, urgent + " need urgent review", urgent > 0 ? "amber" : "seal", "Waiting on a counterparty"),
                new Kpi("compliance", "Compliance posture", String.format(Locale.US, "%.1f%%", posture), null, passing + " of " + cs.size() + " controls passing", posture >= 90 ? "seal" : "amber", "Live result of the last compliance run"),
                new Kpi("volume", "Monthly volume", Long.toString(thisMonth), null, growth, thisMonth >= lastMonth ? "seal" : "alert", "Contracts signed digitally in the last 30 days"));

        Map<String, List<RiskBand>> bands = Map.of(
                "weekly", riskBands(docs, d -> d.getUpdatedAt().isAfter(now.minus(Duration.ofDays(7)))),
                "monthly", riskBands(docs, d -> d.getUpdatedAt().isAfter(now.minus(Duration.ofDays(30)))));

        double reviewDays = docs.stream().filter(d -> d.getStatus() == DocStatus.IN_REVIEW).mapToDouble(d -> days(d.getUpdatedAt(), now)).average().orElse(0);
        long kycPending = memberships.findByOrgId(orgId).stream().filter(m -> m.getUser().getKycStatus() == KycStatus.PENDING).count();
        List<Bottleneck> bottlenecks = List.of(
                new Bottleneck("Legal counsel review", round(reviewDays), "Average wait of documents currently in legal review."),
                new Bottleneck("KYC identity verification", kycPending, kycPending + " member" + (kycPending == 1 ? "" : "s") + " with identity checks still pending."));
        return new Dashboard(kpis, bands, bottlenecks);
    }

    // Heuristic risk: high when value > $250k or no template; moderate when a template was heavily edited; otherwise low.
    static String risk(Document d) {
        if (d instanceof Contract c) {
            if ((c.getValue() != null && c.getValue().compareTo(HIGH_VALUE) > 0) || c.getTemplate() == null) {
                return "high";
            }
            return c.getVersion() > 2 ? "moderate" : "low";
        }
        return "moderate";
    }

    private static List<RiskBand> riskBands(List<Document> docs, Predicate<Document> window) {
        List<Document> in = docs.stream().filter(window).toList();
        long low = in.stream().filter(d -> risk(d).equals("low")).count();
        long moderate = in.stream().filter(d -> risk(d).equals("moderate")).count();
        long high = in.size() - low - moderate;
        return List.of(
                new RiskBand("low", "Low risk (standard MSA / NDA)", percent(low, in.size()), low),
                new RiskBand("moderate", "Moderate risk (custom indemnity clauses)", percent(moderate, in.size()), moderate),
                new RiskBand("high", "High risk (needs legal review)", percent(high, in.size()), high));
    }

    private static Instant lastSignature(Contract c) {
        return c.getParties().stream().map(Party::getSignedAt).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(c.getUpdatedAt());
    }

    private static double days(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).toMinutes() / 1440.0);
    }

    private static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * part / total);
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
