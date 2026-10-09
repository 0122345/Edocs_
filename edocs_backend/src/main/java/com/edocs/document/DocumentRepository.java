package com.edocs.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    @Query("select d from Document d where d.id = :id and d.organization.id = :orgId")
    Optional<Document> findInOrg(UUID id, UUID orgId);

    @Query("select d from Document d where d.organization.id = :orgId order by d.updatedAt desc")
    List<Document> findAllInOrg(UUID orgId);

    // Documents a signer may see: they are a party to it or it was shared with them.
    @Query("""
            select d from Document d where d.organization.id = :orgId and (
              exists (select p.id from Party p where p.contract.id = d.id and p.email = :email)
              or exists (select s.id from DocumentShare s where s.document.id = d.id and s.email = :email))
            order by d.updatedAt desc""")
    List<Document> findVisibleTo(UUID orgId, String email);

    @Query("""
            select count(d) > 0 from Document d where d.id = :id and (
              exists (select p.id from Party p where p.contract.id = d.id and p.email = :email)
              or exists (select s.id from DocumentShare s where s.document.id = d.id and s.email = :email))""")
    boolean isVisibleTo(UUID id, String email);

    long countByOrganizationIdAndStatus(UUID orgId, DocStatus status);
}
