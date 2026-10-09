package com.edocs.security;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PrincipalService principals;

    public JwtAuthUserConverter(PrincipalService principals) {
        this.principals = principals;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthUser user = principals.load(UUID.fromString(jwt.getClaimAsString("org")), UUID.fromString(jwt.getSubject()));
        return new AuthUserAuthentication(user, jwt);
    }
}
