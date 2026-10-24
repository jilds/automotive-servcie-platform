package com.asp.platform.catalogue.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.domain.ServiceDetails;
import com.asp.platform.catalogue.domain.ServiceItem;
import com.asp.platform.catalogue.domain.ServicePricing;
import com.asp.platform.shared.persistence.DomainEntity;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "service_item")
class ServiceItemEntity implements DomainEntity<ServiceItem> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private @Nullable String description;
    private @Nullable String category;

    @Column(name = "standard_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal standardHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServicePricing pricing;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "listed_online", nullable = false)
    private boolean listedOnline;

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
    protected ServiceItemEntity() {
    }

    ServiceItemEntity(ServiceItem item) {
        this.id = item.id();
        apply(item);
    }

    @Override
    public void apply(ServiceItem item) {
        ServiceDetails d = item.details();
        this.name = d.name();
        this.description = d.description();
        this.category = d.category();
        this.standardHours = d.standardHours();
        this.pricing = d.pricing();
        this.priceCents = d.priceCents();
        this.vatRate = d.vatRate();
        this.durationMinutes = d.durationMinutes();
        this.active = d.active();
        this.listedOnline = d.listedOnline();
    }

    @Override
    public ServiceItem toDomain() {
        Long v = version;
        return new ServiceItem(id, new ServiceDetails(name, description, category, standardHours, pricing, priceCents,
                vatRate,
                durationMinutes, active, listedOnline), v == null ? 0 : v);
    }
}
