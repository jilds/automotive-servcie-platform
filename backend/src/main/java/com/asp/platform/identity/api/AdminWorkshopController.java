package com.asp.platform.identity.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.asp.platform.identity.application.BrandingService;
import com.asp.platform.identity.application.WorkshopAdminService;

/// The platform admin's console. `SecurityConfig` already limits `/api/v1/admin/**` to platform admins; the
/// annotation repeats it so the rule stays with the endpoints.
@RestController
@RequestMapping("/api/v1/admin/workshops")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class AdminWorkshopController {

    private final WorkshopAdminService admin;
    private final BrandingService branding;

    AdminWorkshopController(WorkshopAdminService admin, BrandingService branding) {
        this.admin = admin;
        this.branding = branding;
    }

    @GetMapping
    List<AdminWorkshopResponse> list() {
        return admin.workshops().stream().map(AdminWorkshopResponse::of).toList();
    }

    @GetMapping("/{id}")
    AdminWorkshopResponse get(@PathVariable UUID id) {
        return AdminWorkshopResponse.of(admin.workshop(id));
    }

    /// Registers a workshop and its first owner; the response carries the owner's one-time activation link.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProvisionedWorkshopResponse create(@Valid @RequestBody CreateWorkshopRequest request) {
        return ProvisionedWorkshopResponse.of(admin.provision(request.toCommand()));
    }

    /// Plan, status and end date. SUSPENDED or CANCELLED locks the workshop's users out at their next request.
    @PutMapping("/{id}/subscription")
    AdminWorkshopResponse subscription(@PathVariable UUID id, @Valid @RequestBody SubscriptionRequest request) {
        return AdminWorkshopResponse.of(admin.changeSubscription(id, request.toSubscription(), request.version()));
    }

    /// A new activation link for the workshop's first owner; the previous unused link stops working.
    @PostMapping("/{id}/owner-activation-link")
    ActivationLinkResponse ownerLink(@PathVariable UUID id) {
        return ActivationLinkResponse.of(admin.reissueOwnerInvitation(id).link());
    }

    @GetMapping("/{id}/branding")
    BrandingResponse getBranding(@PathVariable UUID id) {
        return BrandingResponse.of(branding.current(id));
    }

    @PutMapping("/{id}/branding")
    BrandingResponse updateBranding(@PathVariable UUID id, @Valid @RequestBody AdminBrandingRequest request) {
        return BrandingResponse.of(branding.update(id, request.slug(), request.toBranding(), request.version()));
    }

    @PutMapping(path = "/{id}/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BrandingResponse uploadLogo(@PathVariable UUID id, @RequestPart("file") MultipartFile file) throws IOException {
        return BrandingResponse.of(branding.uploadLogo(id, file.getBytes()));
    }

    @DeleteMapping("/{id}/branding/logo")
    BrandingResponse removeLogo(@PathVariable UUID id) {
        return BrandingResponse.of(branding.removeLogo(id));
    }
}
