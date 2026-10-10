package com.edocs.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import com.edocs.config.EdocsProperties;

// Envelope encryption: each document gets its own AES-256 data key, wrapped by the master key (KEK).
@Component
@DependsOn("secretsGuard")
public class EnvelopeCrypto {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    public EnvelopeCrypto(EdocsProperties props) {
        byte[] key = Base64.getDecoder().decode(props.crypto().masterKey());
        if (key.length != 32) {
            throw new IllegalStateException("edocs.crypto.master-key must be a base64 encoded 256-bit key");
        }
        this.masterKey = new SecretKeySpec(key, "AES");
    }

    public SecretKey newDataKey() {
        try {
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(256, random);
            return gen.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String wrap(SecretKey dataKey) {
        return Base64.getEncoder().encodeToString(seal(masterKey, dataKey.getEncoded()));
    }

    public SecretKey unwrap(String wrapped) {
        return new SecretKeySpec(open(masterKey, Base64.getDecoder().decode(wrapped)), "AES");
    }

    public String encrypt(String plaintext, SecretKey dataKey) {
        return Base64.getEncoder().encodeToString(seal(dataKey, plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public String decrypt(String ciphertext, SecretKey dataKey) {
        return new String(open(dataKey, Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    private byte[] seal(SecretKey key, byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    private byte[] open(SecretKey key, byte[] sealed) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            return cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed: data was tampered with or the key is wrong", e);
        }
    }
}
