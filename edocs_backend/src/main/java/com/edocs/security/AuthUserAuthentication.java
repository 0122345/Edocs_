package com.edocs.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

public class AuthUserAuthentication extends AbstractAuthenticationToken {

    private final AuthUser user;
    private final Jwt jwt;

    public AuthUserAuthentication(AuthUser user, Jwt jwt) {
        super(RolePermissions.authorities(user.role()));
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public AuthUser getPrincipal() {
        return user;
    }

    public Jwt getJwt() {
        return jwt;
    }
}
