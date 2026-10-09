package com.edocs.document;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.document.DocumentDtos.ArchiveMetricDto;
import com.edocs.document.DocumentDtos.ArchiveRecordDto;
import com.edocs.document.DocumentDtos.DocumentDto;
import com.edocs.document.DocumentDtos.LegalHoldRequest;
import com.edocs.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Archive")
@RestController
@RequestMapping("/archive")
public class ArchiveController {

    private final ArchiveService archive;
    private final DocumentAccess access;

    public ArchiveController(ArchiveService archive, DocumentAccess access) {
        this.archive = archive;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public List<DocumentDto> search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "all") String format,
            @RequestParam(defaultValue = "any") String hold, @RequestParam(defaultValue = "false") boolean sealed) {
        return archive.search(new ArchiveService.Query(q, format, hold, sealed));
    }

    @GetMapping("/metrics")
    public List<ArchiveMetricDto> metrics() {
        return archive.metrics(CurrentUser.get().orgId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('audit:read')")
    public ArchiveRecordDto record(@PathVariable String id) {
        return archive.record(id, access);
    }

    @PutMapping("/{id}/legal-hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('settings:manage')")
    public void legalHold(@PathVariable String id, @RequestBody LegalHoldRequest req) {
        archive.legalHold(id, req.on(), access);
    }
}
