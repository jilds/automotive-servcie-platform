package com.asp.platform.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.Logo;
import com.asp.platform.identity.domain.TenantLogoRepository;

@Repository
class JpaTenantLogoRepository implements TenantLogoRepository {

    private final SpringDataTenantLogoRepository jpa;

    JpaTenantLogoRepository(SpringDataTenantLogoRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(UUID tenantId, Logo logo) {
        jpa.findById(tenantId).ifPresentOrElse(existing -> existing.apply(logo),
                () -> jpa.save(new TenantLogoEntity(tenantId, logo)));
    }

    @Override
    public Optional<Logo> find(UUID tenantId) {
        return jpa.findById(tenantId).map(TenantLogoEntity::toDomain);
    }

    @Override
    public Optional<String> hashOf(UUID tenantId) {
        return jpa.findHash(tenantId);
    }

    @Override
    public void delete(UUID tenantId) {
        jpa.deleteById(tenantId);
    }
}
