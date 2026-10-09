package com.edocs.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    @Query("select w from Workflow w where w.document.id = :docId")
    Optional<Workflow> findByDocumentId(UUID docId);

    @Query("select w from Workflow w where w.document.id in :docIds")
    List<Workflow> findByDocumentIds(Collection<UUID> docIds);

    @Modifying
    @Query("delete from Workflow w where w.document.id = :docId")
    void deleteByDocumentId(UUID docId);
}
