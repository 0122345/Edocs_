package com.edocs.document;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.document.DocumentDtos.CommentDto;
import com.edocs.document.DocumentDtos.CommentRequest;
import com.edocs.document.DocumentDtos.CreateDocumentRequest;
import com.edocs.document.DocumentDtos.DocumentDto;
import com.edocs.document.DocumentDtos.SaveDocumentRequest;
import com.edocs.document.DocumentDtos.ShareRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Documents")
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    public List<DocumentDto> list() {
        return documents.list();
    }

    @GetMapping("/{id}")
    public DocumentDto get(@PathVariable String id) {
        return documents.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('document:create')")
    public DocumentDto create(@Valid @RequestBody CreateDocumentRequest req) {
        return documents.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('document:edit')")
    public DocumentDto save(@PathVariable String id, @Valid @RequestBody SaveDocumentRequest req) {
        return documents.save(id, req.content(), req.note());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('document:delete')")
    public void delete(@PathVariable String id) {
        documents.delete(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto comment(@PathVariable String id, @Valid @RequestBody CommentRequest req) {
        return documents.addComment(id, req.body());
    }

    @PostMapping("/{id}/comments/{commentId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable String id, @PathVariable String commentId) {
        documents.resolveComment(id, commentId);
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('document:share')")
    public void share(@PathVariable String id, @Valid @RequestBody ShareRequest req) {
        documents.share(id, req.email(), req.access());
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('document:share')")
    public DocumentDto send(@PathVariable String id) {
        return documents.send(id);
    }

    @PostMapping("/{id}/parties/{partyId}/remind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('document:share')")
    public void remind(@PathVariable String id, @PathVariable String partyId) {
        documents.remind(id, partyId);
    }
}
