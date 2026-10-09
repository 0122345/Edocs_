package com.edocs.workflow;

import java.util.UUID;

import com.edocs.identity.Organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "routing_rules")
public class RoutingRule {

    public static final String ROUTE_HIGH_VALUE_TO_CFO = "high-value-cfo";

    @Id
    @Column(name = "rule_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    public RoutingRule(Organization organization, String code, String label, String description, boolean enabled) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.code = code;
        this.label = label;
        this.description = description;
        this.enabled = enabled;
    }
}
