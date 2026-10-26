package com.asp.platform.config.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import com.asp.platform.identity.AccessDirectory;
import com.asp.platform.identity.TokenClaims;
import com.asp.platform.shared.Role;

/// Stateless JWT authentication. Authorization rules (which role may call what) live with each feature.
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    /// The public workshop pages (ADR 0004): anonymous, and an `Authorization` header is not even read, so a stale or
    /// invalid token never answers 401 there and a signed-in user's token never picks the workshop: the slug does
    /// (`PublicWorkshopFilter`).
    @Bean
    @Order(1)
    SecurityFilterChain publicPagesFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/public/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(SecurityConfig::securityHeaders)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /// Scripts, styles, fonts, images and API calls only from this origin, plus Google Fonts (`index.html`) and
    /// `data:` / `blob:` images (logo previews). Inline styles are allowed because Angular and Bootstrap set them;
    /// inline scripts are not (the theme script is `theme-init.js`, and the build does not inline critical CSS).
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob:",
            "connect-src 'self'",
            "manifest-src 'self'",
            "worker-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /// The ASP screens use none of these browser features.
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    /// Every response, on both chains:
    /// - the Content-Security-Policy above, and X-Frame-Options DENY (Spring's default) for older browsers;
    /// - HSTS for a year, sent on HTTPS requests only (behind the proxy, `forward-headers-strategy` makes them HTTPS);
    /// - Referrer-Policy `same-origin`: public status links carry their token in the path
    ///   (`/w/{slug}/booking/{token}`), so a link followed from the page must not send that URL to another site;
    /// - Permissions-Policy denying camera, microphone, location, payment and USB.
    private static void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(true))
                .referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .permissionsPolicyHeader(policy -> policy.policy(PERMISSIONS_POLICY));
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessDirectory accessDirectory) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(SecurityConfig::securityHeaders)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/activate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/activation", "/api/v1/platform/contact",
                                "/api/v1/branding/*", "/api/v1/branding/*/logo").permitAll()
                        // The platform admin's console, and nothing else for platform admins: they belong to no
                        // workshop, so every other API (customers, board, dashboard, ...) answers them 403.
                        .requestMatchers("/api/v1/admin/**").hasRole(TokenClaims.PLATFORM_ADMIN)
                        .requestMatchers("/api/**").hasAnyRole(workshopRoles())
                        // Everything outside /api is the Angular app, its static files, health/info and API docs.
                        .anyRequest().permitAll())
                // Role, active state and the workshop's subscription come from the database on every request
                // (cached briefly), so a deactivated or demoted user's older token loses its rights on the next
                // call, and so does every token of a suspended workshop.
                .oauth2ResourceServer(rs -> rs
                        .authenticationEntryPoint(new ProblemBearerEntryPoint())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new CurrentAccessAuthenticationConverter(accessDirectory))));
        return http.build();
    }

    @Bean
    JwtEncoder jwtEncoder(AppSecurityProperties properties) {
        return NimbusJwtEncoder.withSecretKey(signingKey(properties)).build();
    }

    @Bean
    JwtDecoder jwtDecoder(AppSecurityProperties properties) {
        return NimbusJwtDecoder.withSecretKey(signingKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static String[] workshopRoles() {
        return Arrays.stream(Role.values()).map(Role::name).toArray(String[]::new);
    }

    private static SecretKey signingKey(AppSecurityProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
