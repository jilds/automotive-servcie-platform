package com.asp.platform.catalogue.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.asp.platform.catalogue.domain.ServiceItem;
import com.asp.platform.catalogue.domain.ServiceItemRepository;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaServiceItemRepository implements ServiceItemRepository {

    private final SpringDataServiceItemRepository jpa;

    JpaServiceItemRepository(SpringDataServiceItemRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ServiceItem save(ServiceItem item) {
        return Upserts.upsert(jpa, item.id(), item, ServiceItemEntity::new);
    }

    @Override
    public Optional<ServiceItem> findById(UUID id) {
        return jpa.findById(id).map(ServiceItemEntity::toDomain);
    }

    @Override
    public List<ServiceItem> findAllById(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(ServiceItemEntity::toDomain).toList();
    }

    @Override
    public List<ServiceItem> search(@Nullable String query, boolean activeOnly) {
        return jpa.search(query, activeOnly).stream().map(ServiceItemEntity::toDomain).toList();
    }
}
