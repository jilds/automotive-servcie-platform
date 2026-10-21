package com.asp.platform.identity.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.identity.application.WorkshopSettingsService;
import com.asp.platform.shared.CurrentUser;

/// The signed-in user's own workshop; there is no way to address another one.
@RestController
@RequestMapping("/api/v1/workshop/settings")
class WorkshopSettingsController {

    private final WorkshopSettingsService settings;

    WorkshopSettingsController(WorkshopSettingsService settings) {
        this.settings = settings;
    }

    /// The front desk reads the default VAT and labour rate when it adds lines.
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkshopSettingsResponse get(CurrentUser user) {
        return WorkshopSettingsResponse.of(settings.settings(user.tenantId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    WorkshopSettingsResponse update(CurrentUser user, @Valid @RequestBody WorkshopSettingsRequest request) {
        return WorkshopSettingsResponse.of(settings.update(user.tenantId(), request.toSettings()));
    }
}
