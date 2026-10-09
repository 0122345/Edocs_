package com.edocs.compliance;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.compliance.ComplianceService.ControlDto;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Compliance")
@RestController
@RequestMapping("/compliance")
public class ComplianceController {

    private final ComplianceService compliance;

    public ComplianceController(ComplianceService compliance) {
        this.compliance = compliance;
    }

    @GetMapping("/controls")
    public List<ControlDto> controls() {
        return compliance.list();
    }

    @PostMapping("/checks")
    @PreAuthorize("hasAuthority('compliance:run')")
    public List<ControlDto> run() {
        return compliance.run();
    }
}
