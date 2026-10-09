package com.edocs.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.edocs.config.EdocsProperties;

// Issues HS256 bearer tokens consumed by the OAuth2 resource server.
@Service
public class JwtService {

    public static final String ISSUER = "edocs";

    private final JwtEncoder encoder;
    private final EdocsProperties props;

    public JwtService(JwtEncoder encoder, EdocsProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issue(AuthUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.userId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.security().tokenTtl()))
                .claim("org", user.orgId().toString())
                .claim("role", user.role().name())
                .claim("email", user.email())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
