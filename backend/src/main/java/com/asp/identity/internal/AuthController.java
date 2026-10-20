package com.asp.identity.internal;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.shared.tenancy.TenantContext;

@RestController
@RequestMapping("/api/v1")
class AuthController {

    private final IdentityService identity;

    AuthController(IdentityService identity) {
        this.identity = identity;
    }

    /** Registers a new workshop and its owner account. */
    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.TokenResponse signup(@Valid @RequestBody AuthDtos.SignupRequest req) {
        return identity.signup(req);
    }

    @PostMapping("/auth/login")
    AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return identity.login(req);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    AuthDtos.UserResponse invite(@Valid @RequestBody AuthDtos.InviteRequest req) {
        return identity.invite(TenantContext.require(), req);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    List<AuthDtos.UserResponse> users() {
        return identity.list(TenantContext.require());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
}
