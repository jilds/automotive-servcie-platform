package com.asp.platform.identity.infrastructure;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.asp.platform.identity.TokenClaims;
import com.asp.platform.identity.application.IssuedToken;
import com.asp.platform.identity.application.TokenIssuer;
import com.asp.platform.identity.domain.User;

/// Signs HS256 access tokens with the app's JWT key.
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final TokenProperties properties;
    private final Clock clock;

    JwtTokenIssuer(JwtEncoder encoder, TokenProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim(TokenClaims.TENANT_ID, user.tenantId().toString())
                .claim(TokenClaims.ROLE, user.role().name())
                .claim(TokenClaims.EMAIL, user.email())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, properties.ttl());
    }
}
