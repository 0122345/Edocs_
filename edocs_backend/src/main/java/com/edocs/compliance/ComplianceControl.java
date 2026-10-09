package com.edocs.compliance;

import java.time.Instant;
import java.util.UUID;

import com.edocs.identity.Organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "compliance_controls")
public class ComplianceControl {

    public enum Status { PASS, WARN }

    @Id
    @Column(name = "control_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String framework;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Status status;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    public ComplianceControl(Organization organization, String code, String name, String framework, Status status, Instant lastCheckedAt) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.code = code;
        this.name = name;
        this.framework = framework;
        this.status = status;
        this.lastCheckedAt = lastCheckedAt;
    }
}
