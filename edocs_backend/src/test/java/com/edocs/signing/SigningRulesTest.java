package com.edocs.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.edocs.common.ApiException;
import com.edocs.document.Contract;
import com.edocs.document.DocStatus;
import com.edocs.document.Party;
import com.edocs.document.PartyRole;
import com.edocs.identity.Role;
import com.edocs.security.AuthUser;

class SigningRulesTest {

    private final SigningService service = new SigningService(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    private Contract contract;
    private Party first;
    private Party second;

    @BeforeEach
    void setUp() {
        contract = new Contract(null, null, "MSA", "Commercial");
        contract.setStatus(DocStatus.OUT_FOR_SIGNATURE);
        first = new Party("Ana", "ana@acme.test", PartyRole.SIGNER, 1);
        second = new Party("Ben", "ben@partner.test", PartyRole.SIGNER, 2);
        contract.addParty(first);
        contract.addParty(second);
        contract.addParty(new Party("Viv", "viv@acme.test", PartyRole.VIEWER, 3));
    }

    private static AuthUser user(String email) {
        return new AuthUser(UUID.randomUUID(), UUID.randomUUID(), email, "Someone", Role.SIGNER);
    }

    @Test
    void firstSignerMayStart() {
        assertThat(service.pendingPartyFor(contract, user("ana@acme.test"))).isSameAs(first);
    }

    @Test
    void secondSignerMustWaitForTheFirst() {
        assertThatThrownBy(() -> service.pendingPartyFor(contract, user("ben@partner.test")))
                .isInstanceOf(ApiException.class).hasMessageContaining("Waiting for Ana");
        first.setSignedAt(Instant.now());
        assertThat(service.pendingPartyFor(contract, user("ben@partner.test"))).isSameAs(second);
    }

    @Test
    void outsidersAndViewersCannotSign() {
        assertThatThrownBy(() -> service.pendingPartyFor(contract, user("eve@evil.test"))).hasMessageContaining("not a pending signer");
        assertThatThrownBy(() -> service.pendingPartyFor(contract, user("viv@acme.test"))).hasMessageContaining("not a pending signer");
    }

    @Test
    void cannotSignTwice() {
        first.setSignedAt(Instant.now());
        assertThatThrownBy(() -> service.pendingPartyFor(contract, user("ana@acme.test"))).hasMessageContaining("not a pending signer");
    }

    @Test
    void draftsAndSealedDocumentsCannotBeSigned() {
        contract.setStatus(DocStatus.DRAFT);
        assertThatThrownBy(() -> service.pendingPartyFor(contract, user("ana@acme.test"))).hasMessageContaining("not been sent");
        contract.setStatus(DocStatus.SIGNED);
        assertThatThrownBy(() -> service.pendingPartyFor(contract, user("ana@acme.test"))).hasMessageContaining("already fully signed");
    }

    @Test
    void contractIsCompleteOnlyWhenEverySignerSigned() {
        first.setSignedAt(Instant.now());
        assertThat(contract.allSignersSigned()).isFalse();
        second.setSignedAt(Instant.now());
        assertThat(contract.allSignersSigned()).isTrue();
    }

    @Test
    void masksAddresses() {
        assertThat(SigningService.mask("marcus@partnercorp.io")).isEqualTo("m•••s@partnercorp.io");
        assertThat(SigningService.mask("+250788123456")).isEqualTo("•••3456");
    }
}
