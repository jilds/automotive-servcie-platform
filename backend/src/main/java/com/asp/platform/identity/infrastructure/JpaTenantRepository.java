package com.asp.platform.identity.infrastructure;

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
        return jpa.save(new TenantEntity(tenant)).toDomain();
    }
}
