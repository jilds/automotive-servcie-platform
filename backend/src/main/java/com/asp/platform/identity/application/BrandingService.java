package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.domain.Branding;
import com.asp.platform.identity.domain.Logo;
import com.asp.platform.identity.domain.LogoNotFoundException;
import com.asp.platform.identity.domain.Slug;
import com.asp.platform.identity.domain.SlugTakenException;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantLogoRepository;
import com.asp.platform.identity.domain.TenantNotFoundException;
import com.asp.platform.identity.domain.TenantRepository;

/// Workshop themes. Neither the tenant nor the logo table is under row-level security, so methods that change a
/// theme take the tenant id of the signed-in owner (or the workshop the platform admin chose), never one from a
/// request body; public lookups go by slug and return presentation only.
@Service
public class BrandingService {

    private final TenantRepository tenants;
    private final TenantLogoRepository logos;
    private final PublicWorkshopCache publicWorkshops;
    private final Clock clock;
    private final SubscriptionPolicy subscriptions;

    BrandingService(TenantRepository tenants, TenantLogoRepository logos, PublicWorkshopCache publicWorkshops,
            Clock clock, SubscriptionPolicy subscriptions) {
        this.subscriptions = subscriptions;
        this.tenants = tenants;
        this.publicWorkshops = publicWorkshops;
        this.logos = logos;
        this.clock = clock;
    }

    /// The theme of the branded login page `/w/{slug}`. Unknown and malformed slugs read as not found, and so do
    /// suspended, cancelled and expired workshops (ADR 0004): the page then shows the plain ASP sign-in and does not
    /// reveal that the workshop exists.
    @Transactional(readOnly = true)
    public BrandingView bySlug(String slug) {
        Tenant tenant = tenantBySlug(slug);
        return BrandingView.of(tenant, logos.hashOf(tenant.id()).orElse(null));
    }

    @Transactional(readOnly = true)
    public BrandingView current(UUID tenantId) {
        Tenant tenant = tenant(tenantId);
        return BrandingView.of(tenant, logos.hashOf(tenantId).orElse(null));
    }

    @Transactional(readOnly = true)
    public Logo logo(String slug) {
        return logos.find(tenantBySlug(slug).id()).orElseThrow(LogoNotFoundException::new);
    }

    /// The owner changes name and colours; the slug (the branded login address) is the platform admin's to set.
    @Transactional
    public BrandingView update(UUID tenantId, Branding branding, long expectedVersion) {
        return update(tenantId, null, branding, expectedVersion);
    }

    /// @param slug            the new branded login address, or null to keep the current one
    /// @param expectedVersion the workshop version the editor read
    /// @throws SlugTakenException when another workshop already uses the slug
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the workshop since
    @Transactional
    public BrandingView update(UUID tenantId, @Nullable String slug, Branding branding, long expectedVersion) {
        Tenant tenant = tenant(tenantId);
        tenant.requireVersion(expectedVersion);
        Slug newSlug = slug == null ? tenant.slug() : new Slug(slug);
        if (!newSlug.equals(tenant.slug()) && tenants.existsBySlug(newSlug)) {
            throw new SlugTakenException();
        }
        Tenant saved = tenants.save(tenant.withBranding(newSlug, branding));
        // The public pages show the display name and are found by the slug.
        publicWorkshops.evictWorkshop(tenantId, tenant.slug().value());
        publicWorkshops.evictSlug(newSlug.value());
        return BrandingView.of(saved, logos.hashOf(tenantId).orElse(null));
    }

    /// @throws com.asp.platform.identity.domain.InvalidLogoException when the file is refused
    @Transactional
    public BrandingView uploadLogo(UUID tenantId, byte[] content) {
        Tenant tenant = tenant(tenantId);
        Logo logo = Logo.upload(content, clock.instant());
        logos.save(tenantId, logo);
        return BrandingView.of(tenant, logo.sha256());
    }

    @Transactional
    public BrandingView removeLogo(UUID tenantId) {
        Tenant tenant = tenant(tenantId);
        if (logos.hashOf(tenantId).isPresent()) {
            logos.delete(tenantId);
        }
        return BrandingView.of(tenant, null);
    }

    private Tenant tenantBySlug(String slug) {
        if (!Slug.isValid(slug)) {
            throw new TenantNotFoundException();
        }
        return tenants.findBySlug(new Slug(slug))
                .filter(subscriptions::allowsSignIn)
                .orElseThrow(TenantNotFoundException::new);
    }

    private Tenant tenant(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(TenantNotFoundException::new);
    }
}
