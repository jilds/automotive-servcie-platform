package com.asp.platform.identity.application;

import java.util.UUID;

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

    WorkshopSettingsService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public WorkshopSettings settings(UUID tenantId) {
        return tenant(tenantId).settings();
    }

    @Transactional
    public WorkshopSettings update(UUID tenantId, WorkshopSettings settings) {
        return tenants.save(tenant(tenantId).withSettings(settings)).settings();
    }

    private Tenant tenant(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(TenantNotFoundException::new);
    }
}
