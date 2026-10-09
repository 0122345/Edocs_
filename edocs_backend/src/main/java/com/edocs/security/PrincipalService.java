package com.edocs.security;

import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edocs.identity.Membership;
import com.edocs.identity.MembershipRepository;

// Loads the caller's live role and status (briefly cached) so admin changes apply without re-login.
@Service
public class PrincipalService {

    public static final String CACHE = "principals";

    private final MembershipRepository memberships;

    public PrincipalService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE, key = "#orgId + ':' + #userId")
    public AuthUser load(UUID orgId, UUID userId) {
        Membership m = memberships.findByOrgIdAndUserId(orgId, userId)
                .orElseThrow(() -> new DisabledException("Membership not found"));
        if (!m.isActive()) {
            throw new DisabledException("This account is deactivated.");
        }
        var u = m.getUser();
        return new AuthUser(u.getId(), orgId, u.getEmail(), u.getFullName(), u.getRole());
    }

    @CacheEvict(cacheNames = CACHE, key = "#orgId + ':' + #userId")
    public void evict(UUID orgId, UUID userId) {
        // Eviction only.
    }
}
