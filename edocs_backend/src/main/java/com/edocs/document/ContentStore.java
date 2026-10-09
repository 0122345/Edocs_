package com.edocs.document;

import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.edocs.common.ApiException;
import com.edocs.common.HtmlSanitizer;
import com.edocs.common.crypto.EnvelopeCrypto;
import com.edocs.document.DocumentContent.CommentEntry;
import com.edocs.document.DocumentContent.VersionSnapshot;

// Reads and writes the encrypted document body and its history in MongoDB.
@Component
public class ContentStore {

    private final DocumentContentRepository repo;
    private final EnvelopeCrypto crypto;

    public ContentStore(DocumentContentRepository repo, EnvelopeCrypto crypto) {
        this.repo = repo;
        this.crypto = crypto;
    }

    // Generates the document's data key, encrypts the first version and returns the wrapped key for Postgres.
    public String create(Document doc, String html, String authorName, String note, Instant at) {
        SecretKey key = crypto.newDataKey();
        String clean = HtmlSanitizer.clean(html);
        String ct = crypto.encrypt(clean, key);
        DocumentContent content = new DocumentContent(doc.getId().toString(), doc.getOrganization().getId().toString(), ct);
        content.getVersions().add(new VersionSnapshot(UUID.randomUUID().toString(), 1, "Version 1.0 (initial draft)", note, authorName, at, ct));
        repo.save(content);
        doc.setEncryptedKey(crypto.wrap(key));
        sizeFrom(doc, clean);
        return clean;
    }

    public DocumentContent load(Document doc) {
        return repo.findById(doc.getId().toString())
                .orElseThrow(() -> ApiException.notFound("That document's content is missing."));
    }

    public String plaintext(Document doc, DocumentContent content) {
        return crypto.decrypt(content.getCiphertext(), crypto.unwrap(doc.getEncryptedKey()));
    }

    public String plaintext(Document doc) {
        return plaintext(doc, load(doc));
    }

    public String saveVersion(Document doc, String html, String authorName, String note) {
        DocumentContent content = load(doc);
        String clean = HtmlSanitizer.clean(html);
        String ct = crypto.encrypt(clean, crypto.unwrap(doc.getEncryptedKey()));
        content.setCiphertext(ct);
        content.getVersions().add(new VersionSnapshot(UUID.randomUUID().toString(), doc.getVersion(),
                "Version 1." + (doc.getVersion() - 1), note == null || note.isBlank() ? "Saved draft" : note, authorName, Instant.now(), ct));
        repo.save(content);
        sizeFrom(doc, clean);
        return clean;
    }

    public CommentEntry addComment(Document doc, String authorId, String authorName, String initials, String body) {
        DocumentContent content = load(doc);
        CommentEntry c = new CommentEntry(UUID.randomUUID().toString(), authorId, authorName, initials, body, Instant.now(), false);
        content.getComments().add(c);
        repo.save(content);
        return c;
    }

    public void resolveComment(Document doc, String commentId) {
        DocumentContent content = load(doc);
        CommentEntry c = content.getComments().stream().filter(x -> x.getId().equals(commentId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("That comment no longer exists."));
        c.setResolved(true);
        repo.save(content);
    }

    public void delete(Document doc) {
        repo.deleteById(doc.getId().toString());
    }

    // Rough size and page estimate from the HTML body (about 500 words per page).
    private static void sizeFrom(Document doc, String html) {
        int bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int words = html.replaceAll("<[^>]+>", " ").trim().split("\\s+").length;
        doc.setSizeKb(Math.max(1, (int) Math.ceil(bytes / 1024.0)));
        doc.setPages(Math.max(1, (int) Math.ceil(words / 500.0)));
    }
}
