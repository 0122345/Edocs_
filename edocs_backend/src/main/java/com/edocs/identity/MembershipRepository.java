package com.edocs.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @EntityGraph(attributePaths = {"user", "organization"})
    @Query("select m from Membership m where m.user.id = :userId order by m.joinedAt")
    List<Membership> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    @Query("select m from Membership m where m.organization.id = :orgId order by m.joinedAt")
    List<Membership> findByOrgId(UUID orgId);

    @EntityGraph(attributePaths = {"user", "organization"})
    @Query("select m from Membership m where m.organization.id = :orgId and m.user.id = :userId")
    Optional<Membership> findByOrgIdAndUserId(UUID orgId, UUID userId);

    @Query("select m from Membership m where m.organization.id = :orgId and m.user.email = :email")
    Optional<Membership> findByOrgIdAndEmail(UUID orgId, String email);
}
