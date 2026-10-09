package com.edocs.signing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SignatureRepository extends JpaRepository<Signature, UUID> {

    @Query("select s from Signature s where s.document.id = :docId order by s.signedAt")
    List<Signature> findByDocumentId(UUID docId);
}
