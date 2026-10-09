package com.edocs.document;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentShareRepository extends JpaRepository<DocumentShare, UUID> {

    @Query("select s from DocumentShare s where s.document.id = :docId and s.email = :email")
    Optional<DocumentShare> findByDocumentAndEmail(UUID docId, String email);

    @Modifying
    @Query("delete from DocumentShare s where s.document.id = :docId")
    void deleteByDocumentId(UUID docId);
}
