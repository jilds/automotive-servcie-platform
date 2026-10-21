package com.asp.platform.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;

@Repository
class JpaTenantRepository implements TenantRepository {

    private final SpringDataTenantRepository jpa;

    JpaTenantRepository(SpringDataTenantRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantEntity entity = jpa.findById(tenant.id())
                .map(existing -> {
                    existing.apply(tenant);
                    return existing;
                })
                .orElseGet(() -> jpa.save(new TenantEntity(tenant)));
        return entity.toDomain();
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return jpa.findById(id).map(TenantEntity::toDomain);
    }
}
