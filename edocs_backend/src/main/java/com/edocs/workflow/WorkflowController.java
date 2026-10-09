package com.edocs.workflow;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.workflow.WorkflowDtos.RoutingRuleDto;
import com.edocs.workflow.WorkflowDtos.WorkflowView;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Workflow")
@RestController
public class WorkflowController {

    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping("/documents/{id}/workflow")
    public WorkflowView workflow(@PathVariable String id) {
        return workflows.view(id);
    }

    @GetMapping("/documents/{id}/evidence")
    @PreAuthorize("hasAuthority('audit:read')")
    public Map<String, Object> evidence(@PathVariable String id) {
        return workflows.evidence(id);
    }

    @GetMapping("/workflow/routing-rules")
    public List<RoutingRuleDto> rules() {
        return workflows.rules();
    }

    @PutMapping("/workflow/routing-rules")
    @PreAuthorize("hasAuthority('workflow:manage')")
    public List<RoutingRuleDto> saveRules(@RequestBody List<@Valid RoutingRuleDto> rules) {
        return workflows.saveRules(rules);
    }
}
