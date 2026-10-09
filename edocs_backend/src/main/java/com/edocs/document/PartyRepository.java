package com.edocs.document;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartyRepository extends JpaRepository<Party, UUID> {

    @Query("select count(p) from Party p where p.contract.organization.id = :orgId and p.role = com.edocs.document.PartyRole.SIGNER and p.signedAt is null and p.contract.status = com.edocs.document.DocStatus.OUT_FOR_SIGNATURE")
    long countPendingSigners(java.util.UUID orgId);
}
