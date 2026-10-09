package com.edocs.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.edocs.document.DocStatus;
import com.edocs.document.Document;
import com.edocs.identity.Membership;
import com.edocs.identity.Organization;
import com.edocs.identity.Role;
import com.edocs.identity.SignatureLevel;
import com.edocs.identity.User;

class ComplianceEvaluationTest {

    private final Organization org = new Organization("Acme", "acme.test", "admin@acme.test");

    private Membership admin(boolean mfa) {
        User u = new User("Admin", "admin@acme.test", Role.ADMIN);
        u.setMfaEnabled(mfa);
        return new Membership(u, org, LocalDate.now());
    }

    @Test
    void privilegedAccessRequiresMfaForAdminsWhen2faIsRequired() {
        org.setRequire2fa(true);
        assertThat(ComplianceService.evaluate(ComplianceService.ACCESS_REVIEW, org, List.of(), List.of(admin(true)))).isTrue();
        assertThat(ComplianceService.evaluate(ComplianceService.ACCESS_REVIEW, org, List.of(), List.of(admin(false)))).isFalse();
        org.setRequire2fa(false);
        assertThat(ComplianceService.evaluate(ComplianceService.ACCESS_REVIEW, org, List.of(), List.of(admin(false)))).isTrue();
    }

    @Test
    void eidasNeedsQualifiedSignatures() {
        org.setSignatureLevel(SignatureLevel.AES);
        assertThat(ComplianceService.evaluate(ComplianceService.EIDAS, org, List.of(), List.of())).isFalse();
        org.setSignatureLevel(SignatureLevel.QES);
        assertThat(ComplianceService.evaluate(ComplianceService.EIDAS, org, List.of(), List.of())).isTrue();
    }

    @Test
    void archivedContractsMustBeUnderLegalHold() {
        Document archived = new Document(org, null, "Old terms", "Commercial");
        archived.setStatus(DocStatus.ARCHIVED);
        assertThat(ComplianceService.evaluate(ComplianceService.RETENTION, org, List.of(archived), List.of())).isFalse();
        archived.setLegalHold(true);
        assertThat(ComplianceService.evaluate(ComplianceService.RETENTION, org, List.of(archived), List.of())).isTrue();
    }

    @Test
    void encryptionAtRestNeedsAWrappedKeyOnEveryDocument() {
        Document doc = new Document(org, null, "NDA", "Confidentiality");
        assertThat(ComplianceService.evaluate(ComplianceService.ENCRYPTION, org, List.of(doc), List.of())).isFalse();
        doc.setEncryptedKey("wrapped");
        assertThat(ComplianceService.evaluate(ComplianceService.ENCRYPTION, org, List.of(doc), List.of())).isTrue();
    }

    @Test
    void unknownControlsFailClosed() {
        assertThat(ComplianceService.evaluate("made-up", org, List.of(), List.of())).isFalse();
    }
}
