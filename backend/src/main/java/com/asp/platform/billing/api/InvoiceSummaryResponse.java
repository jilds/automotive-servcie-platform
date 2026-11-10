package com.asp.platform.billing.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.InvoiceQueries;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.PaymentProgress;

/// One row of the invoice list.
record InvoiceSummaryResponse(UUID id, DocumentKind kind, InvoiceStatus status, @Nullable String number,
        UUID workOrderId, int workOrderNumber, String customerName, @Nullable String customerNif,
        @Nullable String plate, @Nullable Instant issuedAt, Instant createdAt, long totalCents, long balanceCents, long refundDueCents,
        @Nullable PaymentProgress paymentStatus, boolean sandbox) {

    record Page(List<InvoiceSummaryResponse> items, int page, int size, long total) {
    }

    static Page of(InvoiceQueries.Page page) {
        return new Page(page.items().stream().map(InvoiceSummaryResponse::of).toList(), page.page(), page.size(),
                page.total());
    }

    private static InvoiceSummaryResponse of(InvoiceQueries.Row r) {
        return new InvoiceSummaryResponse(r.id(), r.kind(), r.status(), r.number(), r.workOrderId(),
                r.workOrderNumber(), r.customerName(), r.customerNif(), r.plate(), r.issuedAt(), r.createdAt(),
                r.totalCents(), r.balanceCents(), r.refundDueCents(), r.progress(), "sandbox".equals(r.provider()));
    }
}
