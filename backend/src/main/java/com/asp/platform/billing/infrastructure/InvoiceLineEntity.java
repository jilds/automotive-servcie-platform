package com.asp.platform.billing.infrastructure;

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

import com.asp.platform.billing.domain.InvoiceLine;
import com.asp.platform.billing.domain.LineKind;

/// A work order line as it was when the invoice was issued. Only inserted: the app role may not update or delete these
/// rows (V20). `tenant_id` comes from the connection.
@Entity
@Table(name = "invoice_line")
class InvoiceLineEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private InvoiceEntity invoice;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LineKind kind;

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

    @Column(name = "source_line_id", updatable = false)
    private @Nullable UUID sourceLineId;

    @SuppressWarnings("NullAway.Init")
    protected InvoiceLineEntity() {
    }

    InvoiceLineEntity(InvoiceEntity invoice, InvoiceLine line) {
        this.id = line.id();
        this.invoice = invoice;
        this.position = line.position();
        this.kind = line.kind();
        this.description = line.description();
        this.quantity = line.quantity();
        this.unitPriceCents = line.unitPriceCents();
        this.discountPercent = line.discountPercent();
        this.vatRate = line.vatRate();
        this.netCents = line.netCents();
        this.vatCents = line.vatCents();
        this.sourceLineId = line.sourceLineId();
    }

    InvoiceLine toDomain() {
        return new InvoiceLine(id, position, kind, description, quantity, unitPriceCents, discountPercent, vatRate,
                netCents, vatCents, sourceLineId);
    }
}
