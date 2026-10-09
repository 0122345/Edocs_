package com.edocs.document;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.document.DocumentDtos.CreateTemplateRequest;
import com.edocs.document.DocumentDtos.TemplateDto;
import com.edocs.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Templates")
@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateService templates;

    public TemplateController(TemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    public List<TemplateDto> list() {
        return templates.list(CurrentUser.get().orgId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('template:manage')")
    public TemplateDto create(@Valid @RequestBody CreateTemplateRequest req) {
        return templates.create(req.name(), req.category());
    }
}
