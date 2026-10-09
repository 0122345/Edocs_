package com.edocs.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edocs.document.Document;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A contract triggers 0..1 workflow; step 5 means every step is complete.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workflows")
public class Workflow {

    public static final int COMPLETE = 5;

    public enum Status { ACTIVE, COMPLETED, CANCELLED }

    public enum TriggerType { MANUAL, ON_CREATE, ON_SEND }

    @Id
    @Column(name = "workflow_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", unique = true)
    private Document document;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10)
    private TriggerType trigger = TriggerType.ON_CREATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "current_step", nullable = false)
    private int currentStep = 1;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<WorkflowStep> steps = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Workflow(Document document, String name) {
        this.id = UUID.randomUUID();
        this.document = document;
        this.name = name;
    }

    public void addStep(WorkflowStep step) {
        step.setWorkflow(this);
        steps.add(step);
    }

    public void advanceTo(int step) {
        currentStep = Math.max(currentStep, Math.min(step, COMPLETE));
        if (currentStep == COMPLETE) {
            status = Status.COMPLETED;
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
