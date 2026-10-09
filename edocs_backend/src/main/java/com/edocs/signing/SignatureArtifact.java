package com.edocs.signing;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Raw signature capture (drawn image or typed name) kept in MongoDB; Postgres keeps only its hash.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document("signature_artifacts")
public class SignatureArtifact {

    @Id
    private String signatureId;
    @Indexed
    private String documentId;
    private String mode;
    private String data;
    private Instant capturedAt;
}
