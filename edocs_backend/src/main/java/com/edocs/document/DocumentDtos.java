package com.edocs.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Request/response shapes mirroring edocs_frontend/src/data/types.ts.
public final class DocumentDtos {

    private DocumentDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartyDto(String id, String name, String email, PartyRole role, int order, Instant signedAt) {
    }

    public record CommentDto(String id, String author, String initials, String ago, String body, boolean resolved) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VersionDto(String id, String label, String when, String by, String note, boolean current) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DocumentDto(
            String id, String title, String category, String templateId, DocFormat format, DocStatus status, int version,
            String content, List<PartyDto> parties, BigDecimal value, LocalDate effectiveDate, LocalDate expiryDate,
            String ownerId, Instant createdAt, Instant updatedAt, boolean legalHold, String holdUntil, int workflowStep,
            List<CommentDto> comments, List<VersionDto> versions, String sha256, String txId, int sizeKb, int pages) {
    }

    public record PartyInput(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull PartyRole role,
            @Min(1) int order) {
    }

    public record CreateDocumentRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 80) String category,
            String templateId,
            @NotNull @Size(max = 20) List<@Valid PartyInput> parties,
            @PositiveOrZero BigDecimal value,
            LocalDate effectiveDate,
            LocalDate expiryDate) {
    }

    public record SaveDocumentRequest(@NotNull @Size(max = 2_000_000) String content, @Size(max = 200) String note) {
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record ShareRequest(@NotBlank @Email String email, @NotNull DocumentShare.Access access) {
    }

    public record TemplateDto(String id, String name, String category, int uses, String updated, String content, List<String> variables) {
    }

    public record CreateTemplateRequest(@NotBlank @Size(max = 200) String name, @Size(max = 80) String category) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEntryDto(String id, String kind, String title, String timestamp, String body, String hash) {
    }

    public record ArchiveRecordDto(DocumentDto document, List<TimelineEntryDto> timeline) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ArchiveMetricDto(String label, String value, String tone) {
    }

    public record LegalHoldRequest(boolean on) {
    }
}
