package com.edocs.signing;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.common.ApiException;
import com.edocs.common.Hashing;
import com.edocs.config.EdocsProperties;
import com.edocs.signing.OtpChallenge.Purpose;

@Service
public class OtpService {

    private final OtpChallengeRepository repo;
    private final EdocsProperties props;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository repo, EdocsProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public record Issued(OtpChallenge challenge, String code) {
    }

    public Issued issue(UUID userId, UUID documentId, Purpose purpose) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpChallenge c = new OtpChallenge();
        c.setId(UUID.randomUUID());
        c.setUserId(userId);
        c.setDocumentId(documentId);
        c.setPurpose(purpose);
        c.setCodeHash(hash(c.getId(), code));
        c.setCreatedAt(Instant.now());
        c.setExpiresAt(c.getCreatedAt().plus(props.otp().ttl()));
        return new Issued(repo.save(c), code);
    }

    // Wrong guesses are committed even when the caller's transaction rolls back, so the attempt cap holds.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(UUID challengeId, String code, String acceptAlso) {
        OtpChallenge c = repo.findById(challengeId)
                .orElseThrow(() -> ApiException.unprocessable("That code is wrong or has expired. Send a new code and try again."));
        if (c.getConsumedAt() != null || c.getExpiresAt().isBefore(Instant.now()) || c.getAttempts() >= props.otp().maxAttempts()) {
            throw ApiException.unprocessable("That code is wrong or has expired. Send a new code and try again.");
        }
        boolean ok = code != null && (hash(c.getId(), code.trim()).equals(c.getCodeHash())
                || (acceptAlso != null && !acceptAlso.isBlank() && acceptAlso.equals(code.trim())));
        if (!ok) {
            c.setAttempts(c.getAttempts() + 1);
            repo.save(c);
            throw ApiException.unprocessable("That code is incorrect. Check the code we sent you and try again.");
        }
        c.setConsumedAt(Instant.now());
        repo.save(c);
    }

    public OtpChallenge latestSigning(UUID userId, UUID documentId) {
        return repo.findLatestSigning(userId, documentId)
                .orElseThrow(() -> ApiException.unprocessable("That code is wrong or has expired. Send a new code and try again."));
    }

    static String hash(UUID salt, String code) {
        return Hashing.sha256Hex(salt.toString(), code);
    }
}
