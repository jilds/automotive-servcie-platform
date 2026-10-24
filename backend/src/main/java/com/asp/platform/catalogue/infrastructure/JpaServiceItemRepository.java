package com.asp.platform.catalogue.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.asp.platform.catalogue.domain.ServiceItem;
import com.asp.platform.catalogue.domain.ServiceItemRepository;

@Repository
class JpaServiceItemRepository implements ServiceItemRepository {

    private final SpringDataServiceItemRepository jpa;

    JpaServiceItemRepository(SpringDataServiceItemRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ServiceItem save(ServiceItem item) {
        ServiceItemEntity entity = jpa.findById(item.id())
                .map(existing -> {
                    existing.apply(item);
                    return existing;
                })
                .orElseGet(() -> jpa.save(new ServiceItemEntity(item)));
        jpa.flush(); // so the returned version is the stored one
        return entity.toDomain();
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
