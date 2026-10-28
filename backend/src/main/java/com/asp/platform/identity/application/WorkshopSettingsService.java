package com.asp.platform.identity.application;

import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantNotFoundException;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.WorkshopSettings;

/// Workshop settings. The tenant table has no row-level security, so every method takes the signed-in
/// user's tenant id and never one from the request. Lookups for other modules live in
/// [WorkshopDirectoryService].
@Service
public class WorkshopSettingsService {

    private final TenantRepository tenants;
    private final PublicWorkshopCache publicWorkshops;

    WorkshopSettingsService(TenantRepository tenants, PublicWorkshopCache publicWorkshops) {
        this.tenants = tenants;
        this.publicWorkshops = publicWorkshops;
    }

    @Transactional(readOnly = true)
    public Tenant workshop(UUID tenantId) {
        return tenant(tenantId);
    }

    /// @param change          makes the new settings from the stored ones (a request may leave fields out)
    /// @param expectedVersion the workshop version the editor read (settings, branding and subscription share it)
    /// @throws com.asp.platform.shared.StaleDataException  when someone else saved the workshop since
    /// @throws com.asp.platform.shared.InvalidNifException when a Portuguese NIF is not valid
    @Transactional
    public Tenant update(UUID tenantId, UnaryOperator<WorkshopSettings> change, long expectedVersion) {
        Tenant tenant = tenant(tenantId);
        tenant.requireVersion(expectedVersion);
        Tenant saved = tenants.save(tenant.withSettings(change.apply(tenant.settings()).checkNif()));
        // The public pages show the name, country and time zone.
        publicWorkshops.evictWorkshop(tenantId, tenant.slug().value());
        return saved;
    }

    private Tenant tenant(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(TenantNotFoundException::new);
    }
}
