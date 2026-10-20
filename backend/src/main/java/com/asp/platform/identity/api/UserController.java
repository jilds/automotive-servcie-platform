package com.asp.platform.identity.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.identity.application.IdentityService;
import com.asp.platform.shared.CurrentUser;

/// The owner manages the workshop's team.
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('OWNER')")
class UserController {

    private final IdentityService identity;

    UserController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse invite(CurrentUser user, @Valid @RequestBody InviteUserRequest request) {
        return UserResponse.of(identity.invite(user.tenantId(), request.toCommand()));
    }

    @GetMapping
    List<UserResponse> list(CurrentUser user) {
        return identity.usersOf(user.tenantId()).stream().map(UserResponse::of).toList();
    }
}
