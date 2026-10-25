package com.asp.platform.booking.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.asp.platform.booking.domain.BookedService;

/// A booked service as the catalogue offered it then; never changed.
@Entity
@Table(name = "booking_service")
class BookingServiceEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private BookingEntity booking;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "service_item_id", nullable = false, updatable = false)
    private UUID serviceItemId;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false, updatable = false)
    private int durationMinutes;

    @Column(name = "price_cents", nullable = false, updatable = false)
    private long priceCents;

    @Column(name = "vat_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @SuppressWarnings("NullAway.Init")
    protected BookingServiceEntity() {
    }

    BookingServiceEntity(BookingEntity booking, int position, BookedService service) {
        this.id = UUID.randomUUID();
        this.booking = booking;
        this.position = position;
        this.serviceItemId = service.serviceItemId();
        this.name = service.name();
        this.durationMinutes = service.durationMinutes();
        this.priceCents = service.priceCents();
        this.vatRate = service.vatRate();
    }

    BookedService toDomain() {
        return new BookedService(serviceItemId, name, durationMinutes, priceCents, vatRate);
    }
}
