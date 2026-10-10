package com.edocs.signing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditLog;
import com.edocs.audit.AuditService;
import com.edocs.common.ApiException;
import com.edocs.common.ClientInfo;
import com.edocs.common.Hashing;
import com.edocs.config.CacheConfig;
import com.edocs.config.EdocsProperties;
import com.edocs.document.ContentStore;
import com.edocs.document.Contract;
import com.edocs.document.DocStatus;
import com.edocs.document.DocumentAccess;
import com.edocs.document.DocumentRepository;
import com.edocs.document.DocumentService;
import com.edocs.document.Party;
import com.edocs.identity.Organization;
import com.edocs.identity.OrganizationRepository;
import com.edocs.identity.User;
import com.edocs.identity.UserRepository;
import com.edocs.messaging.DomainEvent;
import com.edocs.messaging.MessagePublisher;
import com.edocs.messaging.SmsGateway;
import com.edocs.notification.Channel;
import com.edocs.notification.NotificationService;
import com.edocs.notification.NotificationService.Recipient;
import com.edocs.notification.NotificationType;
import com.edocs.security.AuthUser;
import com.edocs.security.CurrentUser;
import com.edocs.signing.OtpChallenge.Purpose;
import com.edocs.signing.SigningDtos.OtpSent;
import com.edocs.signing.SigningDtos.SignResult;
import com.edocs.workflow.Workflow;
import com.edocs.workflow.WorkflowService;

@Service
public class SigningService {

    private final DocumentAccess access;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final ContentStore contents;
    private final SignatureRepository signatures;
    private final SignatureArtifactRepository artifacts;
    private final OtpService otps;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final WorkflowService workflows;
    private final AuditService audit;
    private final NotificationService notifications;
    private final MessagePublisher publisher;
    private final EdocsProperties props;
    private final SmsGateway smsGateway;

    public SigningService(DocumentAccess access, DocumentRepository documents, DocumentService documentService, ContentStore contents,
            SignatureRepository signatures, SignatureArtifactRepository artifacts, OtpService otps, UserRepository users,
            OrganizationRepository organizations, WorkflowService workflows, AuditService audit, NotificationService notifications,
            MessagePublisher publisher, EdocsProperties props, SmsGateway smsGateway) {
        this.access = access;
        this.documents = documents;
        this.documentService = documentService;
        this.contents = contents;
        this.signatures = signatures;
        this.artifacts = artifacts;
        this.otps = otps;
        this.users = users;
        this.organizations = organizations;
        this.workflows = workflows;
        this.audit = audit;
        this.notifications = notifications;
        this.publisher = publisher;
        this.props = props;
        this.smsGateway = smsGateway;
    }

    // Sends a one-time code to the signer via RabbitMQ: SMS when a phone is on file and a real SMS provider is wired, otherwise email.
    @Transactional
    public OtpSent sendOtp(String id) {
        AuthUser me = CurrentUser.get();
        Contract doc = access.readableContract(id, me);
        pendingPartyFor(doc, me);
        User user = users.findById(me.userId()).orElseThrow();
        OtpService.Issued issued = otps.issue(me.userId(), doc.getId(), Purpose.SIGNING);
        String minutes = String.valueOf(props.otp().ttl().toMinutes());
        String wire = "Your Edocs signing code is " + issued.code() + ". It expires in " + minutes + " minutes.";
        boolean sms = smsGateway.delivers() && user.getPhone() != null && !user.getPhone().isBlank();
        notifications.send(me.orgId(), new Recipient(me.userId(), me.email(), user.getPhone()), NotificationType.OTP,
                sms ? Channel.SMS : Channel.EMAIL, "Your signing code", "A signing code for “" + doc.getTitle() + "” was sent to you.", null, wire);
        if (props.otp().echoInApp()) {
            notifications.send(me.orgId(), Recipient.user(me.userId(), me.email()), NotificationType.OTP, Channel.IN_APP,
                    "Your signing code", wire, null);
        }
        return new OtpSent(sms ? mask(user.getPhone()) : mask(me.email()));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.DASHBOARD, CacheConfig.ARCHIVE_METRICS}, allEntries = true)
    public SignResult sign(String id, String mode, String otp, String signatureData) {
        AuthUser me = CurrentUser.get();
        Contract doc = access.readableContract(id, me);
        Party party = pendingPartyFor(doc, me);
        OtpChallenge challenge = otps.latestSigning(me.userId(), doc.getId());
        otps.verify(challenge.getId(), otp, null);

        Organization org = organizations.findById(me.orgId()).orElseThrow();
        Instant now = Instant.now();
        String ip = ClientInfo.ip();
        String body = contents.plaintext(doc);
        String evidence = Hashing.sha256Hex(doc.getId().toString(), Integer.toString(doc.getVersion()), body, signatureData, me.email(), ip, now.toString());

        Signature sig = new Signature();
        sig.setId(UUID.randomUUID());
        sig.setDocument(doc);
        sig.setParty(party);
        sig.setSigner(users.getReferenceById(me.userId()));
        sig.setType(org.getSignatureLevel());
        sig.setMode(mode);
        sig.setSignedAt(now);
        sig.setIpAddress(ip);
        sig.setEvidenceHash(evidence);
        sig.setCertificate("CN=" + me.name() + ", E=" + me.email() + ", O=" + org.getName() + "; level=" + org.getSignatureLevel()
                + "; serial=" + sig.getId() + "; issuer=Edocs Development Trust Service (simulated QTSP)");
        signatures.save(sig);
        artifacts.save(new SignatureArtifact(sig.getId().toString(), doc.getId().toString(), mode, signatureData, now));

        party.setSignedAt(now);
        doc.setUpdatedAt(now);
        audit.record(AuditKind.SIGNATURE, "E-signature executed (" + mode + ")", doc);
        Workflow w = workflows.of(doc);

        String txId;
        if (doc.allSignersSigned()) {
            List<String> evidenceHashes = signatures.findByDocumentId(doc.getId()).stream().map(Signature::getEvidenceHash).toList();
            List<String> auditHashes = audit.forDocument(me.orgId(), doc.getId()).stream().map(AuditLog::getHash).toList();
            doc.setStatus(DocStatus.SIGNED);
            doc.setSha256(Hashing.sha256Hex(body, String.join(",", evidenceHashes)));
            doc.setAnchorTx("0x" + Hashing.merkleRoot(auditHashes));
            w.advanceTo(Workflow.COMPLETE);
            txId = doc.getAnchorTx();
            for (Party p : doc.getParties()) {
                notifications.send(me.orgId(), Recipient.external(p.getEmail()), NotificationType.SIGNED, Channel.EMAIL,
                        doc.getTitle() + " fully signed", "All parties signed “" + doc.getTitle() + "”. The sealed copy is in the archive.", null);
            }
            notifications.send(me.orgId(), Recipient.user(doc.getOwner().getId(), doc.getOwner().getEmail()), NotificationType.SIGNED, Channel.IN_APP,
                    doc.getTitle() + " fully signed", "All parties signed. The sealed copy is in the archive.", "/archive?doc=" + id);
            publisher.event(event(DomainEvent.DOCUMENT_FULLY_SIGNED, doc.getId(), me));
        } else {
            doc.setStatus(DocStatus.OUT_FOR_SIGNATURE);
            w.advanceTo(4);
            txId = "0x" + evidence;
            nextSigner(doc).ifPresent(next -> notifications.send(me.orgId(), Recipient.external(next.getEmail()), NotificationType.SIGNATURE_REQUEST,
                    Channel.EMAIL, "Your turn to sign", me.name() + " signed “" + doc.getTitle() + "”. It is now waiting for you.", "/sign/" + id));
            publisher.event(event(DomainEvent.DOCUMENT_SIGNED, doc.getId(), me));
        }
        documents.save(doc);
        return new SignResult(Hashing.shortTx(txId), documentService.full(doc));
    }

    // The caller must be an unsigned signer, and every signer before them in signatureOrder must have signed.
    Party pendingPartyFor(Contract doc, AuthUser me) {
        if (doc.getStatus().isSealed()) {
            throw ApiException.conflict("This document is already fully signed.");
        }
        if (doc.getStatus() != DocStatus.OUT_FOR_SIGNATURE) {
            throw ApiException.conflict("This document has not been sent for signature yet.");
        }
        Party mine = doc.signers().stream()
                .filter(p -> p.getEmail().equalsIgnoreCase(me.email()) && p.getSignedAt() == null)
                .findFirst()
                .orElseThrow(() -> ApiException.forbidden("You are not a pending signer on this document."));
        nextSigner(doc).filter(next -> next.getSignatureOrder() < mine.getSignatureOrder()).ifPresent(next -> {
            throw ApiException.conflict("Waiting for " + next.getName() + " to sign first.");
        });
        return mine;
    }

    private static java.util.Optional<Party> nextSigner(Contract doc) {
        return doc.signers().stream().filter(p -> p.getSignedAt() == null).min(java.util.Comparator.comparingInt(Party::getSignatureOrder));
    }

    static String mask(String address) {
        int at = address.indexOf('@');
        if (at > 1) {
            return address.charAt(0) + "•••" + address.substring(at - 1);
        }
        return address.length() > 4 ? "•••" + address.substring(address.length() - 4) : address;
    }

    private static DomainEvent event(String type, UUID docId, AuthUser me) {
        return new DomainEvent(type, me.orgId().toString(), docId.toString(), me.userId().toString(), me.email(), Instant.now());
    }
}
