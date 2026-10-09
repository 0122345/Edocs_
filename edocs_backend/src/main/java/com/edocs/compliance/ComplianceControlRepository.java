package com.edocs.compliance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComplianceControlRepository extends JpaRepository<ComplianceControl, UUID> {

    @Query("select c from ComplianceControl c where c.organization.id = :orgId order by c.code")
    List<ComplianceControl> findAllInOrg(UUID orgId);
}
