package com.edocs.document;

import java.util.Comparator;
import java.util.List;

import com.edocs.common.Hashing;
import com.edocs.common.TimeFormat;
import com.edocs.document.DocumentContent.VersionSnapshot;
import com.edocs.document.DocumentDtos.CommentDto;
import com.edocs.document.DocumentDtos.DocumentDto;
import com.edocs.document.DocumentDtos.PartyDto;
import com.edocs.document.DocumentDtos.TemplateDto;
import com.edocs.document.DocumentDtos.VersionDto;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    // Summary form for lists: no body, comments or history.
    public static DocumentDto summary(Document d, int workflowStep) {
        return toDto(d, workflowStep, "", null);
    }

    public static DocumentDto full(Document d, int workflowStep, String plaintext, DocumentContent content) {
        return toDto(d, workflowStep, plaintext, content);
    }

    private static DocumentDto toDto(Document d, int workflowStep, String body, DocumentContent content) {
        Contract c = d instanceof Contract contract ? contract : null;
        List<PartyDto> parties = c == null ? List.of() : c.getParties().stream().map(DocumentMapper::party).toList();
        List<CommentDto> comments = content == null ? List.of() : content.getComments().stream()
                .map(x -> new CommentDto(x.getId(), x.getAuthorName(), x.getInitials(), TimeFormat.ago(x.getCreatedAt()), x.getBody(), x.isResolved()))
                .toList();
        List<VersionDto> versions = content == null ? List.of() : versions(content);
        return new DocumentDto(
                d.getId().toString(), d.getTitle(), d.getCategory(),
                c != null && c.getTemplate() != null ? c.getTemplate().getId().toString() : null,
                d.getFormat(), d.getStatus(), d.getVersion(), body, parties,
                c == null ? null : c.getValue(), c == null ? null : c.getEffectiveDate(), c == null ? null : c.getExpiryDate(),
                d.getOwner().getId().toString(), d.getCreatedAt(), d.getUpdatedAt(),
                d.isLegalHold(), TimeFormat.longDate(d.getHoldUntil()), workflowStep,
                comments, versions, d.getSha256(), d.getAnchorTx() == null ? null : Hashing.shortTx(d.getAnchorTx()),
                d.getSizeKb(), d.getPages());
    }

    public static PartyDto party(Party p) {
        return new PartyDto(p.getId().toString(), p.getName(), p.getEmail(), p.getRole(), p.getSignatureOrder(), p.getSignedAt());
    }

    private static List<VersionDto> versions(DocumentContent content) {
        List<VersionSnapshot> sorted = content.getVersions().stream()
                .sorted(Comparator.comparing(VersionSnapshot::getCreatedAt).reversed()).toList();
        return java.util.stream.IntStream.range(0, sorted.size()).mapToObj(i -> {
            VersionSnapshot v = sorted.get(i);
            return new VersionDto(v.getId(), v.getLabel(), TimeFormat.when(v.getCreatedAt()), v.getAuthorName(), v.getNote(), i == 0);
        }).toList();
    }

    public static TemplateDto template(Template t) {
        return new TemplateDto(t.getId().toString(), t.getTitle(), t.getCategory(), t.getUsageCount(),
                TimeFormat.ago(t.getUpdatedAt()), t.getContent(), List.copyOf(t.getVariables()));
    }
}
