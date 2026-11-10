package com.asp.platform.billing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// The invoice list, with the payment progress computed in the database so it can be filtered and paged.
public interface InvoiceQueries {

    /// @param statuses empty means any
    /// @param from     inclusive, on the creation time
    /// @param to       exclusive
    /// @param search   matches the number, customer name, customer NIF or plate (case-insensitive), or null
    record Filter(List<InvoiceStatus> statuses, @Nullable DocumentKind kind, @Nullable PaymentProgress progress,
            @Nullable Instant from, @Nullable Instant to, @Nullable String search) {
    }

    record Row(UUID id, DocumentKind kind, InvoiceStatus status, @Nullable String number, UUID workOrderId,
            int workOrderNumber, String customerName, @Nullable String customerNif, @Nullable String plate,
            @Nullable Instant issuedAt, Instant createdAt, long totalCents, long balanceCents, long refundDueCents,
            @Nullable PaymentProgress progress, String provider) {
    }

    record Page(List<Row> items, int page, int size, long total) {
    }

    /// Newest first.
    Page find(Filter filter, int page, int size);
}
