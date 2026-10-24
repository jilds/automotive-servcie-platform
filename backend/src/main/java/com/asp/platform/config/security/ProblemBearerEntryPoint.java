package com.asp.platform.config.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/// Bearer-token 401s as before (status and `WWW-Authenticate` from Spring Security, empty body), except for a
/// suspended workshop, which also gets an RFC 9457 body with code `workshop-suspended`.
class ProblemBearerEntryPoint implements AuthenticationEntryPoint {

    private static final String SUSPENDED_BODY = """
            {"type":"/problems/workshop-suspended","title":"Unauthorized","status":401,\
            "detail":"This workshop's subscription is suspended; contact the ASP team","code":"workshop-suspended"}""";

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        bearer.commence(request, response, authException);
        if (authException instanceof WorkshopSuspendedAuthenticationException) {
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(SUSPENDED_BODY);
        }
    }
}
