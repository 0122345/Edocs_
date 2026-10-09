package com.edocs.workflow;

import java.util.UUID;

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

// Step definition; its live status is derived from the workflow's current step.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep {

    public enum Completion { COMPLETED, APPROVED, VERIFIED }

    @Id
    @Column(name = "step_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status", nullable = false, length = 10)
    private Completion completionStatus;

    public WorkflowStep(int position, String title, String description, Completion completionStatus) {
        this.id = UUID.randomUUID();
        this.position = position;
        this.title = title;
        this.description = description;
        this.completionStatus = completionStatus;
    }
}
