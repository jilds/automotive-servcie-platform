package com.asp.platform.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import com.asp.platform.identity.TokenClaims;

/// The decoder accepts a token signed with the app's key only when issuer and audience are the app's own.
class JwtDecoderTests {

    private static final String SECRET = "test-only-jwt-secret-for-the-decoder-tests-0123456789";
    private static final String ISSUER = "asp";

    private final JwtDecoder decoder = new SecurityConfig().jwtDecoder(new AppSecurityProperties(SECRET), ISSUER);
    private final JwtEncoder encoder = new SecurityConfig().jwtEncoder(new AppSecurityProperties(SECRET));

    @Test
    void should_accept_a_token_with_the_apps_issuer_and_audience() {
        assertThat(decoder.decode(token(ISSUER, List.of(TokenClaims.AUDIENCE), Instant.now().plusSeconds(60)))
                .getSubject()).isEqualTo("someone");
    }

    @Test
    void should_refuse_a_token_of_another_issuer() {
        assertThatThrownBy(() -> decoder.decode(
                token("someone-else", List.of(TokenClaims.AUDIENCE), Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void should_refuse_a_token_for_another_audience() {
        assertThatThrownBy(() -> decoder.decode(
                token(ISSUER, List.of("another-api"), Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void should_refuse_a_token_without_an_audience() {
        assertThatThrownBy(() -> decoder.decode(token(ISSUER, null, Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void should_still_refuse_an_expired_token() {
        assertThatThrownBy(() -> decoder.decode(
                token(ISSUER, List.of(TokenClaims.AUDIENCE), Instant.now().minusSeconds(3600))))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void should_refuse_a_token_signed_with_another_key() {
        JwtEncoder other = new SecurityConfig().jwtEncoder(
                new AppSecurityProperties("another-secret-that-is-long-enough-0123456789"));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject("someone")
                .audience(List.of(TokenClaims.AUDIENCE)).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
        String forged = other.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(org.springframework.security.oauth2.jwt.BadJwtException.class);
    }

    private String token(String issuer, @Nullable List<String> audience, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject("someone")
                .issuedAt(expiresAt.minusSeconds(120)).expiresAt(expiresAt);
        if (audience != null) {
            claims.audience(audience);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
