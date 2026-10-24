package com.asp.platform.identity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.identity.application.ActivationService;
import com.asp.platform.identity.application.IdentityService;

/// Public endpoints: login for workshop users and platform admins, and activation links. There is no signup:
/// workshops are registered by the platform admin.
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final IdentityService identity;
    private final ActivationService activation;

    AuthController(IdentityService identity, ActivationService activation) {
        this.identity = identity;
        this.activation = activation;
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.of(identity.login(request.email(), request.password()));
    }

    /// Who the link is for, so the page can greet them before they choose a password.
    @GetMapping("/activation")
    ActivationPreviewResponse activation(@RequestParam @NotBlank @Size(max = 100) String token) {
        return ActivationPreviewResponse.of(activation.preview(token));
    }

    /// Sets name and password, uses up the link and signs the user in.
    @PostMapping("/activate")
    TokenResponse activate(@Valid @RequestBody ActivateRequest request) {
        return TokenResponse.of(activation.activate(request.token(), request.name(), request.password()));
    }
}
