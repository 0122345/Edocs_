package com.edocs.document;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.edocs.common.ApiException;
import com.edocs.identity.Role;
import com.edocs.security.AuthUser;

// Resource-level authorization on top of RBAC: tenant isolation plus party/share visibility for signers.
@Component
public class DocumentAccess {

    private final DocumentRepository documents;

    public DocumentAccess(DocumentRepository documents) {
        this.documents = documents;
    }

    public Document readable(String id, AuthUser me) {
        UUID docId = parse(id);
        Document doc = documents.findInOrg(docId, me.orgId()).orElseThrow(DocumentAccess::missing);
        if (me.role() == Role.SIGNER && !documents.isVisibleTo(docId, me.email())) {
            // Same answer as "missing" so signers cannot probe for other documents.
            throw missing();
        }
        return doc;
    }

    public Contract readableContract(String id, AuthUser me) {
        if (readable(id, me) instanceof Contract c) {
            return c;
        }
        throw ApiException.unprocessable("This document has no parties to sign.");
    }

    static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw missing();
        }
    }

    static ApiException missing() {
        return ApiException.notFound("That document no longer exists.");
    }
}
