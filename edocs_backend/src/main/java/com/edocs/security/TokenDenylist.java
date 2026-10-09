package com.edocs.security;

import org.springframework.stereotype.Component;

import com.edocs.config.EdocsProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

// Revoked token ids (logout). Entries expire with the token, so the set stays small.
@Component
public class TokenDenylist {

    private final Cache<String, Boolean> revoked;

    public TokenDenylist(EdocsProperties props) {
        this.revoked = Caffeine.newBuilder().expireAfterWrite(props.security().tokenTtl()).maximumSize(100_000).build();
    }

    public void revoke(String jti) {
        if (jti != null) {
            revoked.put(jti, Boolean.TRUE);
        }
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.getIfPresent(jti) != null;
    }
}
