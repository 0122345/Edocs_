package com.edocs.workflow;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.document.DocStatus;
import com.edocs.document.DocumentRepository;
import com.edocs.identity.MembershipRepository;
import com.edocs.identity.Organization;
import com.edocs.identity.OrganizationRepository;
import com.edocs.identity.Role;
import com.edocs.notification.Channel;
import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.edocs.notification.NotificationType;

// Implements the "Escalate stalled legal reviews" routing rule: daily nudge to legal for reviews older than 2 days.
@Component
public class ReviewEscalationJob {

    private static final Logger log = LoggerFactory.getLogger(ReviewEscalationJob.class);
    private static final String RULE = "escalate-review";

    private final OrganizationRepository organizations;
    private final DocumentRepository documents;
    private final MembershipRepository memberships;
    private final RoutingRuleRepository rules;
    private final NotificationService notifications;

    public ReviewEscalationJob(OrganizationRepository organizations, DocumentRepository documents, MembershipRepository memberships,
            RoutingRuleRepository rules, NotificationService notifications) {
        this.organizations = organizations;
        this.documents = documents;
        this.memberships = memberships;
        this.rules = rules;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${edocs.escalation-cron:0 0 8 * * *}", zone = "UTC")
    @Transactional(readOnly = true)
    public void escalate() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(2));
        for (Organization org : organizations.findAll()) {
            boolean enabled = rules.findAllInOrg(org.getId()).stream().anyMatch(r -> r.getCode().equals(RULE) && r.isEnabled());
            if (!enabled) {
                continue;
            }
            documents.findAllInOrg(org.getId()).stream()
                    .filter(d -> d.getStatus() == DocStatus.IN_REVIEW && d.getUpdatedAt().isBefore(cutoff))
                    .forEach(d -> memberships.findByOrgId(org.getId()).stream()
                            .filter(m -> m.isActive() && m.getUser().getRole() == Role.LEGAL)
                            .forEach(m -> notifications.send(org.getId(), Recipient.user(m.getUser().getId(), m.getUser().getEmail()),
                                    NotificationType.SYSTEM, Channel.EMAIL, "Legal review is stalling",
                                    "“" + d.getTitle() + "” has waited more than 2 days for legal review.", "/editor/" + d.getId())));
        }
        log.debug("Review escalation run finished");
    }
}
