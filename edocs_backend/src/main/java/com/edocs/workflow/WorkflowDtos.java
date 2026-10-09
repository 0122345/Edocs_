package com.edocs.workflow;

import java.util.List;

import com.edocs.document.DocumentDtos.DocumentDto;

import jakarta.validation.constraints.NotBlank;

public final class WorkflowDtos {

    private WorkflowDtos() {
    }

    // status: completed | approved | verified | in-progress | waiting
    public record StepDto(int id, String title, String description, String status) {
    }

    // icon: identity | lock | edit
    public record ActivityDto(String id, String icon, String text, String meta) {
    }

    public record WorkflowView(DocumentDto document, List<StepDto> steps, List<ActivityDto> activity) {
    }

    public record RoutingRuleDto(@NotBlank String id, String label, String description, boolean enabled) {
    }
}
