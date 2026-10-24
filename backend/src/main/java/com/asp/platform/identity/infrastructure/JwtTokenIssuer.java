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
import com.asp.platform.identity.domain.PlatformAdmin;
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
        return sign(claims(user.id().toString(), user.email())
                .claim(TokenClaims.TENANT_ID, user.tenantId().toString())
                .claim(TokenClaims.ROLE, user.role().name())
                .build());
    }

    /// No tenant claim: the connection of a platform admin's request never sees a workshop's rows.
    @Override
    public IssuedToken issue(PlatformAdmin admin) {
        return sign(claims(admin.id().toString(), admin.email())
                .claim(TokenClaims.ROLE, TokenClaims.PLATFORM_ADMIN)
                .build());
    }

    private JwtClaimsSet.Builder claims(String subject, String email) {
        Instant now = clock.instant();
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim(TokenClaims.EMAIL, email);
    }

    private IssuedToken sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, properties.ttl());
    }
}
