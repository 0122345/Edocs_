package com.edocs.audit;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    Optional<AuditLog> findTopByOrgIdOrderBySeqDesc(String orgId);

    Page<AuditLog> findByOrgIdOrderByAtDesc(String orgId, Pageable pageable);

    Page<AuditLog> findByOrgIdAndActorIdOrderByAtDesc(String orgId, String actorId, Pageable pageable);

    List<AuditLog> findByOrgIdAndEntityIdOrderByAtDesc(String orgId, String entityId);

    Stream<AuditLog> findByOrgIdOrderBySeqAsc(String orgId);

    List<AuditLog> findByOrgIdAndKind(String orgId, AuditKind kind);

    long countByOrgId(String orgId);
}
