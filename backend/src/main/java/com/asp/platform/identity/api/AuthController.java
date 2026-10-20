package com.asp.platform.identity.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.identity.application.IdentityService;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final IdentityService identity;

    AuthController(IdentityService identity) {
        this.identity = identity;
    }

    /// Registers a new workshop and its owner account.
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return TokenResponse.of(identity.signup(request.toCommand()));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.of(identity.login(request.email(), request.password()));
    }
}
