package com.edocs.document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// MongoDB aggregate holding a document's encrypted body, version snapshots and comment thread.
@Getter
@Setter
@NoArgsConstructor
@org.springframework.data.mongodb.core.mapping.Document("document_contents")
public class DocumentContent {

    @Id
    private String documentId;
    @Indexed
    private String orgId;
    private String ciphertext;
    private List<VersionSnapshot> versions = new ArrayList<>();
    private List<CommentEntry> comments = new ArrayList<>();

    public DocumentContent(String documentId, String orgId, String ciphertext) {
        this.documentId = documentId;
        this.orgId = orgId;
        this.ciphertext = ciphertext;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionSnapshot {
        private String id;
        private int number;
        private String label;
        private String note;
        private String authorName;
        private Instant createdAt;
        private String ciphertext;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentEntry {
        private String id;
        private String authorId;
        private String authorName;
        private String initials;
        private String body;
        private Instant createdAt;
        private boolean resolved;
    }
}
