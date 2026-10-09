package com.edocs.signing;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditService;
import com.edocs.audit.AuditService.Actor;
import com.edocs.audit.AuditStatus;
import com.edocs.document.DocumentRepository;
import com.edocs.messaging.DomainEvent;
import com.edocs.messaging.RabbitConfig;

// Consumes document events. Fully signed documents get their Merkle root "anchored" (simulated public-chain notarization).
@Component
public class AnchorListener {

    private static final Logger log = LoggerFactory.getLogger(AnchorListener.class);

    private final DocumentRepository documents;
    private final AuditService audit;

    public AnchorListener(DocumentRepository documents, AuditService audit) {
        this.documents = documents;
        this.audit = audit;
    }

    @RabbitListener(queues = RabbitConfig.DOCUMENT_EVENTS_QUEUE)
    @Transactional(readOnly = true)
    public void onDocumentEvent(DomainEvent event) {
        log.debug("Document event {} for {}", event.type(), event.documentId());
        if (!DomainEvent.DOCUMENT_FULLY_SIGNED.equals(event.type())) {
            return;
        }
        documents.findById(UUID.fromString(event.documentId())).ifPresent(doc -> audit.append(
                Actor.system(doc.getOrganization().getId(), "anchor-service", "Ethereum Mainnet (simulated)"),
                AuditKind.ANCHOR, "Merkle root anchored", doc.getId().toString(), AuditService.fileName(doc),
                AuditStatus.VERIFIED, Instant.now()));
    }
}
