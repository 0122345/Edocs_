package com.edocs.identity;

import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.audit.AuditKind;
import com.edocs.audit.AuditService;
import com.edocs.common.ApiException;
import com.edocs.config.CacheConfig;
import com.edocs.identity.IdentityDtos.SettingsDto;

@Service
public class SettingsService {

    private final OrganizationRepository organizations;
    private final AuditService audit;

    public SettingsService(OrganizationRepository organizations, AuditService audit) {
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.SETTINGS, key = "#orgId")
    public SettingsDto get(UUID orgId) {
        return toDto(load(orgId));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SETTINGS, key = "#orgId")
    public SettingsDto update(UUID orgId, SettingsDto s) {
        Organization org = load(orgId);
        org.setName(s.orgName().trim());
        org.setAdminEmail(s.adminEmail().trim().toLowerCase());
        org.setSignatureLevel(s.signatureLevel());
        org.setRetentionYears(s.retentionYears());
        org.setRequire2fa(s.require2fa());
        audit.record(AuditKind.KEY_ROTATION, "Workspace settings updated", null);
        return toDto(org);
    }

    private Organization load(UUID orgId) {
        return organizations.findById(orgId).orElseThrow(() -> ApiException.notFound("Workspace not found."));
    }

    private static SettingsDto toDto(Organization o) {
        return new SettingsDto(o.getName(), o.getAdminEmail(), o.getSignatureLevel(), o.getRetentionYears(), o.isRequire2fa());
    }
}
