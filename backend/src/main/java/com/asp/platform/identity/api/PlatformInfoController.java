package com.asp.platform.identity.api;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.identity.application.PlatformProperties;

/// Public: how a workshop gets ASP. Shown on the login page; both values are null when not configured.
@RestController
@RequestMapping("/api/v1/platform")
class PlatformInfoController {

    private final PlatformProperties properties;

    PlatformInfoController(PlatformProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/contact")
    ContactResponse contact() {
        return new ContactResponse(properties.contactEmailOrNull(), properties.contactPhoneOrNull());
    }

    record ContactResponse(@Nullable String email, @Nullable String phone) {
    }
}
