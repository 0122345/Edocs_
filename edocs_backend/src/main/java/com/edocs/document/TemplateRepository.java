package com.edocs.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    @Query("select t from Template t where t.organization.id = :orgId order by t.updatedAt desc")
    List<Template> findAllInOrg(UUID orgId);

    @Query("select t from Template t where t.id = :id and t.organization.id = :orgId")
    Optional<Template> findInOrg(UUID id, UUID orgId);
}
