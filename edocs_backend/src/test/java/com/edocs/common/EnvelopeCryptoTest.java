package com.edocs.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.edocs.common.crypto.EnvelopeCrypto;
import com.edocs.config.EdocsProperties;

class EnvelopeCryptoTest {

    private final EnvelopeCrypto crypto = new EnvelopeCrypto(new EdocsProperties(null, null, null,
            new EdocsProperties.Crypto(Base64.getEncoder().encodeToString(new byte[32])), null, null, null));

    @Test
    void roundTripsThroughWrappedKey() {
        SecretKey key = crypto.newDataKey();
        String wrapped = crypto.wrap(key);
        String ct = crypto.encrypt("<p>Confidential terms</p>", key);
        assertThat(ct).doesNotContain("Confidential");
        assertThat(crypto.decrypt(ct, crypto.unwrap(wrapped))).isEqualTo("<p>Confidential terms</p>");
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime() {
        SecretKey key = crypto.newDataKey();
        assertThat(crypto.encrypt("x", key)).isNotEqualTo(crypto.encrypt("x", key));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        SecretKey key = crypto.newDataKey();
        byte[] bytes = Base64.getDecoder().decode(crypto.encrypt("pay $100", key));
        bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(bytes), key))
                .hasMessageContaining("tampered");
    }

    @Test
    void wrongKeyIsRejected() {
        String ct = crypto.encrypt("secret", crypto.newDataKey());
        assertThatThrownBy(() -> crypto.decrypt(ct, crypto.newDataKey())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsShortMasterKey() {
        assertThatThrownBy(() -> new EnvelopeCrypto(new EdocsProperties(null, null, null,
                new EdocsProperties.Crypto(Base64.getEncoder().encodeToString(new byte[16])), null, null, null)))
                .hasMessageContaining("256-bit");
    }
}
