package com.asp.platform.identity.api;

import com.asp.platform.identity.application.Session;

record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    private static final String BEARER = "Bearer";

    static TokenResponse of(Session session) {
        return new TokenResponse(session.token().value(), BEARER, session.token().ttl().toSeconds(),
                UserResponse.of(session.user()));
    }
}
