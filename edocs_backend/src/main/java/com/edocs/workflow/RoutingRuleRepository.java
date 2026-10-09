package com.edocs.workflow;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {

    @Query("select r from RoutingRule r where r.organization.id = :orgId order by r.code")
    List<RoutingRule> findAllInOrg(UUID orgId);
}
