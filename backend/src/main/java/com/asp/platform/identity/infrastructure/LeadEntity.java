package com.asp.platform.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Lead;
import com.asp.platform.shared.persistence.DomainEntity;

@Entity
@Table(name = "lead")
class LeadEntity implements DomainEntity<Lead> {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(name = "workshop_name", nullable = false, updatable = false)
    private String workshopName;

    @Column(updatable = false)
    private @Nullable String phone;

    @Column(updatable = false)
    private @Nullable String email;

    @Column(updatable = false)
    private @Nullable String district;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, updatable = false)
    private Lead.VehicleType vehicleType;

    @Column(updatable = false)
    private @Nullable String message;

    @Column(name = "privacy_accepted_at", nullable = false, updatable = false)
    private Instant privacyAcceptedAt;

    @Column(name = "marketing_consent", nullable = false, updatable = false)
    private boolean marketingConsent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Lead.Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "status_changed_at")
    private @Nullable Instant statusChangedAt;

    /// Optimistic lock; null until first saved, so Spring Data persists a new lead instead of merging it.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @SuppressWarnings("NullAway.Init")
    protected LeadEntity() {
    }

    LeadEntity(Lead lead) {
        this.id = lead.id();
        this.name = lead.name();
        this.workshopName = lead.workshopName();
        this.phone = lead.phone();
        this.email = lead.email();
        this.district = lead.district();
        this.vehicleType = lead.vehicleType();
        this.message = lead.message();
        this.privacyAcceptedAt = lead.privacyAcceptedAt();
        this.marketingConsent = lead.marketingConsent();
        this.createdAt = lead.createdAt();
        apply(lead);
    }

    /// Only the status moves after the request was received.
    @Override
    public void apply(Lead lead) {
        this.status = lead.status();
        this.statusChangedAt = lead.statusChangedAt();
    }

    @Override
    public Lead toDomain() {
        Long v = version;
        return new Lead(id, name, workshopName, phone, email, district, vehicleType, message, privacyAcceptedAt,
                marketingConsent, status, createdAt, statusChangedAt, v == null ? 0 : v);
    }
}
