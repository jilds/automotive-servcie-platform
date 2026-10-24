package com.asp.platform.identity.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.BrandColor;
import com.asp.platform.identity.domain.Branding;
import com.asp.platform.identity.domain.Plan;
import com.asp.platform.identity.domain.Slug;
import com.asp.platform.identity.domain.Subscription;
import com.asp.platform.identity.domain.SubscriptionStatus;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.WorkshopSettings;
import com.asp.platform.shared.persistence.DomainEntity;

@Entity
@Table(name = "tenant")
class TenantEntity implements DomainEntity<Tenant> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private @Nullable String nif;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "default_vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultVatRate;

    @Column(name = "labour_rate_cents", nullable = false)
    private long labourRateCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "subscription_until")
    private @Nullable LocalDate subscriptionUntil;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "display_name")
    private @Nullable String displayName;

    @Column(name = "primary_color", length = 7)
    private @Nullable String primaryColor;

    @Column(name = "accent_color", length = 7)
    private @Nullable String accentColor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /// Optimistic lock: Hibernate adds `where version = ?` to every update and raises it by one. Null until the
    /// entity is first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @SuppressWarnings("NullAway.Init")
    protected TenantEntity() {
    }

    TenantEntity(Tenant tenant) {
        this.id = tenant.id();
        this.createdAt = tenant.createdAt();
        apply(tenant);
    }

    @Override
    public void apply(Tenant tenant) {
        WorkshopSettings s = tenant.settings();
        this.name = s.name();
        this.nif = s.nif();
        this.country = s.country();
        this.timezone = s.timezone();
        this.defaultVatRate = s.defaultVatRate();
        this.labourRateCents = s.labourRateCents();
        Subscription sub = tenant.subscription();
        this.plan = sub.plan();
        this.status = sub.status();
        this.subscriptionUntil = sub.until();
        this.slug = tenant.slug().value();
        Branding b = tenant.branding();
        this.displayName = b.displayName();
        this.primaryColor = hex(b.primaryColor());
        this.accentColor = hex(b.accentColor());
    }

    @Override
    public Tenant toDomain() {
        return new Tenant(id,
                new WorkshopSettings(name, nif, country, timezone, defaultVatRate, labourRateCents),
                new Subscription(plan, status, subscriptionUntil),
                new Slug(slug),
                new Branding(displayName, color(primaryColor), color(accentColor)),
                createdAt, version());
    }

    private static @Nullable String hex(@Nullable BrandColor color) {
        return color == null ? null : color.hex();
    }

    private static @Nullable BrandColor color(@Nullable String hex) {
        return hex == null ? null : new BrandColor(hex);
    }

    private long version() {
        Long v = version;
        return v == null ? 0 : v;
    }
}
