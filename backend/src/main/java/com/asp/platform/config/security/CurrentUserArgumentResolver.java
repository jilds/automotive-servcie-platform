package com.asp.platform.config.security;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.asp.platform.identity.TokenClaims;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.shared.Role;

/// Lets controllers take a [CurrentUser] parameter, built from the verified JWT and the user's current role.
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No signed-in user");
        }
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(TokenClaims.TENANT_ID)),
                currentRole(auth));
    }

    /// The role checked against the database by [CurrentAccessAuthenticationConverter], not the token's
    /// `role` claim, which may be hours old.
    private static Role currentRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null
                        && authority.startsWith(CurrentAccessAuthenticationConverter.ROLE_PREFIX))
                .map(authority -> Role.valueOf(
                        authority.substring(CurrentAccessAuthenticationConverter.ROLE_PREFIX.length())))
                .findFirst()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No role for the signed-in user"));
    }
}
