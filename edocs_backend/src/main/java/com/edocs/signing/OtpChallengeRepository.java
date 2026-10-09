package com.edocs.signing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    @Query("""
            select c from OtpChallenge c where c.userId = :userId and c.purpose = com.edocs.signing.OtpChallenge.Purpose.SIGNING
              and c.documentId = :docId and c.consumedAt is null order by c.createdAt desc limit 1""")
    Optional<OtpChallenge> findLatestSigning(UUID userId, UUID docId);
}
