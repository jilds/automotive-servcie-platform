package com.asp.platform.shop.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.persistence.DomainEntity;
import com.asp.platform.shop.domain.CancelledBy;
import com.asp.platform.shop.domain.PaymentMethod;
import com.asp.platform.shop.domain.ShopContact;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderLine;
import com.asp.platform.shop.domain.ShopOrderStatus;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security. The
/// contact, consents, token hash, total and lines are written once, when the order is placed.
@Entity
@Table(name = "shop_order")
class ShopOrderEntity implements DomainEntity<ShopOrder> {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopOrderStatus status;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(updatable = false)
    private @Nullable String phone;

    @Column(updatable = false)
    private @Nullable String email;

    @Column(updatable = false)
    private @Nullable String nif;

    @Column(updatable = false)
    private @Nullable String language;

    @Column(name = "privacy_accepted_at", nullable = false, updatable = false)
    private Instant privacyAcceptedAt;

    @Column(name = "marketing_consent", nullable = false, updatable = false)
    private boolean marketingConsent;

    @Column(name = "status_token_hash", nullable = false, updatable = false)
    private String statusTokenHash;

    @Column(name = "total_cents", nullable = false, updatable = false)
    private long totalCents;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "ready_at")
    private @Nullable Instant readyAt;

    @Column(name = "hold_until", nullable = false)
    private Instant holdUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private @Nullable PaymentMethod paymentMethod;

    @Column(name = "collected_by")
    private @Nullable UUID collectedBy;

    @Column(name = "collected_at")
    private @Nullable Instant collectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private @Nullable CancelledBy cancelledBy;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    @Column(name = "cancel_reason")
    private @Nullable String cancelReason;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("position")
    private List<ShopOrderLineEntity> lines = new ArrayList<>();

    @SuppressWarnings("NullAway.Init")
    protected ShopOrderEntity() {
    }

    ShopOrderEntity(ShopOrder order) {
        this.id = order.id();
        this.number = order.number();
        ShopContact c = order.contact();
        this.name = c.name();
        this.phone = c.phone();
        this.email = c.email();
        this.nif = c.nif();
        this.language = order.language();
        this.privacyAcceptedAt = order.privacyAcceptedAt();
        this.marketingConsent = order.marketingConsent();
        this.statusTokenHash = order.statusTokenHash();
        this.totalCents = order.totalCents();
        this.placedAt = order.placedAt();
        this.createdAt = order.placedAt();
        List<ShopOrderLine> ordered = order.lines();
        for (int i = 0; i < ordered.size(); i++) {
            lines.add(new ShopOrderLineEntity(this, i + 1, ordered.get(i)));
        }
        apply(order);
    }

    /// Copies what changes after the order is placed: status, hold, collection and cancellation.
    @Override
    public void apply(ShopOrder order) {
        this.status = order.status();
        this.readyAt = order.readyAt();
        this.holdUntil = order.holdUntil();
        this.paymentMethod = order.paymentMethod();
        this.collectedBy = order.collectedBy();
        this.collectedAt = order.collectedAt();
        this.cancelledBy = order.cancelledBy();
        this.cancelledAt = order.cancelledAt();
        this.cancelReason = order.cancelReason();
    }

    @Override
    public ShopOrder toDomain() {
        Long v = version;
        return new ShopOrder(id, number, status, new ShopContact(name, phone, email, nif), language,
                marketingConsent, privacyAcceptedAt, statusTokenHash,
                lines.stream().map(ShopOrderLineEntity::toDomain).toList(), placedAt, readyAt, holdUntil,
                paymentMethod, collectedBy, collectedAt, cancelledBy, cancelledAt, cancelReason, v == null ? 0 : v);
    }
}
