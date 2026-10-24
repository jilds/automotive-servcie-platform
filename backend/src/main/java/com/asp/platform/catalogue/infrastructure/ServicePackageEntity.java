package com.asp.platform.catalogue.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.domain.PackageDetails;
import com.asp.platform.catalogue.domain.PackageItem;
import com.asp.platform.catalogue.domain.ServicePackage;
import com.asp.platform.shared.persistence.DomainEntity;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "service_package")
class ServicePackageEntity implements DomainEntity<ServicePackage> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private @Nullable String description;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "listed_online", nullable = false)
    private boolean listedOnline;

    @OneToMany(mappedBy = "servicePackage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServicePackageItemEntity> items = new ArrayList<>();

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected ServicePackageEntity() {
    }

    ServicePackageEntity(ServicePackage servicePackage) {
        this.id = servicePackage.id();
        apply(servicePackage);
    }

    /// Items are updated in place (quantity, position), removed or added, never deleted and re-inserted, so the
    /// `unique (package_id, service_item_id)` and `unique (package_id, product_id)` constraints hold whatever order
    /// Hibernate flushes in.
    @Override
    public void apply(ServicePackage servicePackage) {
        PackageDetails d = servicePackage.details();
        this.name = d.name();
        this.description = d.description();
        this.priceCents = d.priceCents();
        this.vatRate = d.vatRate();
        this.active = d.active();
        this.listedOnline = d.listedOnline();
        Map<PackageItem.Key, ServicePackageItemEntity> existing = new HashMap<>();
        items.forEach(item -> existing.put(item.key(), item));
        Set<PackageItem.Key> wanted = d.items().stream().map(PackageItem::key).collect(Collectors.toSet());
        items.removeIf(item -> !wanted.contains(item.key()));
        for (int position = 0; position < d.items().size(); position++) {
            PackageItem item = d.items().get(position);
            ServicePackageItemEntity entity = existing.get(item.key());
            if (entity == null) {
                items.add(new ServicePackageItemEntity(this, item, position));
            } else {
                entity.update(item, position);
            }
        }
    }

    @Override
    public ServicePackage toDomain() {
        Long v = version;
        List<PackageItem> domainItems = items.stream()
                .sorted(Comparator.comparingInt(ServicePackageItemEntity::position))
                .map(ServicePackageItemEntity::toDomain)
                .toList();
        return new ServicePackage(id,
                new PackageDetails(name, description, priceCents, vatRate, active, listedOnline, domainItems),
                v == null ? 0 : v);
    }
}
