package com.edocs.messaging;

import java.time.Instant;

// Payload on edocs.events, e.g. routing key "document.fully_signed".
public record DomainEvent(String type, String orgId, String documentId, String actorId, String actorEmail, Instant occurredAt) {

    public static final String DOCUMENT_CREATED = "document.created";
    public static final String DOCUMENT_SENT = "document.sent";
    public static final String DOCUMENT_SIGNED = "document.signed";
    public static final String DOCUMENT_FULLY_SIGNED = "document.fully_signed";
}
