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
import com.asp.platform.identity.application.ProvisionedWorkshop;
import com.asp.platform.identity.application.SubscriptionPolicy;
import com.asp.platform.identity.application.WorkshopAdminService;
import com.asp.platform.identity.domain.WorkshopOverview;

/// The platform admin's console. `SecurityConfig` already limits `/api/v1/admin/**` to platform admins; the
/// annotation repeats it so the rule stays with the endpoints.
@RestController
@RequestMapping("/api/v1/admin/workshops")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class AdminWorkshopController {

    private final WorkshopAdminService admin;
    private final BrandingService branding;
    private final SubscriptionPolicy subscriptions;

    AdminWorkshopController(WorkshopAdminService admin, BrandingService branding, SubscriptionPolicy subscriptions) {
        this.admin = admin;
        this.branding = branding;
        this.subscriptions = subscriptions;
    }

    private AdminWorkshopResponse response(WorkshopOverview overview) {
        return AdminWorkshopResponse.of(overview, subscriptions.expired(overview.tenant()));
    }

    @GetMapping
    List<AdminWorkshopResponse> list() {
        return admin.workshops().stream().map(this::response).toList();
    }

    @GetMapping("/{id}")
    AdminWorkshopResponse get(@PathVariable UUID id) {
        return response(admin.workshop(id));
    }

    /// Registers a workshop and its first owner; the response carries the owner's one-time activation link.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProvisionedWorkshopResponse create(@Valid @RequestBody CreateWorkshopRequest request) {
        ProvisionedWorkshop provisioned = admin.provision(request.toCommand());
        return ProvisionedWorkshopResponse.of(provisioned, subscriptions.expired(provisioned.workshop().tenant()));
    }

    /// Plan, status and end date. SUSPENDED or CANCELLED locks the workshop's users out at their next request.
    @PutMapping("/{id}/subscription")
    AdminWorkshopResponse subscription(@PathVariable UUID id, @Valid @RequestBody SubscriptionRequest request) {
        return response(admin.changeSubscription(id, request.toSubscription(), request.version()));
    }

    /// Online booking and online shop on or off, independent of the plan. Off answers 404 on their public pages.
    @PutMapping("/{id}/add-ons")
    AdminWorkshopResponse addOns(@PathVariable UUID id, @Valid @RequestBody AddOnsRequest request) {
        return response(admin.changeAddOns(id, request.toAddOns(), request.version()));
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
