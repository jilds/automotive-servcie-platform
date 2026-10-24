package com.asp.platform.identity.infrastructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.Slug;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.WorkshopOverview;

@Repository
class JpaTenantRepository implements TenantRepository {

    private final SpringDataTenantRepository jpa;
    private final SpringDataUserRepository users;

    JpaTenantRepository(SpringDataTenantRepository jpa, SpringDataUserRepository users) {
        this.jpa = jpa;
        this.users = users;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantEntity entity = jpa.findById(tenant.id())
                .map(existing -> {
                    existing.apply(tenant);
                    return existing;
                })
                .orElseGet(() -> jpa.save(new TenantEntity(tenant)));
        jpa.flush(); // so the returned version is the stored one
        return entity.toDomain();
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return jpa.findById(id).map(TenantEntity::toDomain);
    }

    @Override
    public Optional<Tenant> findBySlug(Slug slug) {
        return jpa.findBySlug(slug.value()).map(TenantEntity::toDomain);
    }

    @Override
    public boolean existsBySlug(Slug slug) {
        return jpa.existsBySlug(slug.value());
    }

    /// Three queries whatever the number of workshops: tenants, user counts per tenant, owners by creation time.
    @Override
    public List<WorkshopOverview> overview() {
        Map<UUID, Long> counts = new HashMap<>();
        users.countByTenant().forEach(row -> counts.put(row.getTenantId(), row.getUsers()));
        Map<UUID, String> firstOwner = new HashMap<>();
        users.ownersByCreation().forEach(row -> firstOwner.putIfAbsent(row.getTenantId(), row.getEmail()));
        return jpa.findAllByOrderByCreatedAtDesc().stream()
                .map(TenantEntity::toDomain)
                .map(t -> new WorkshopOverview(t, firstOwner.get(t.id()), counts.getOrDefault(t.id(), 0L)))
                .toList();
    }
}
