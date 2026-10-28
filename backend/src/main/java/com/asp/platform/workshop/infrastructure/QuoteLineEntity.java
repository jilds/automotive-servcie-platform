package com.asp.platform.workshop.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.QuoteLine;

/// A work order line as it was when the quote was sent. Only inserted: the app role may not update or delete these
/// rows (V18). `tenant_id` comes from the connection.
@Entity
@Table(name = "quote_line")
class QuoteLineEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private QuoteEntity quote;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LineType type;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price_cents", nullable = false, updatable = false)
    private long unitPriceCents;

    @Column(name = "discount_percent", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "vat_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "net_cents", nullable = false, updatable = false)
    private long netCents;

    @Column(name = "vat_cents", nullable = false, updatable = false)
    private long vatCents;

    @Column(name = "service_item_id", updatable = false)
    private @Nullable UUID serviceItemId;

    @Column(name = "product_id", updatable = false)
    private @Nullable UUID productId;

    @SuppressWarnings("NullAway.Init")
    protected QuoteLineEntity() {
    }

    QuoteLineEntity(QuoteEntity quote, QuoteLine line) {
        this.id = line.id();
        this.quote = quote;
        this.position = line.position();
        this.type = line.type();
        this.description = line.description();
        this.quantity = line.quantity();
        this.unitPriceCents = line.unitPriceCents();
        this.discountPercent = line.discountPercent();
        this.vatRate = line.vatRate();
        this.netCents = line.netCents();
        this.vatCents = line.vatCents();
        this.serviceItemId = line.serviceItemId();
        this.productId = line.productId();
    }

    QuoteLine toDomain() {
        return new QuoteLine(id, position, type, description, quantity, unitPriceCents, discountPercent, vatRate,
                netCents, vatCents, serviceItemId, productId);
    }
}
