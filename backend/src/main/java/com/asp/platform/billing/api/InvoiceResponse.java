package com.asp.platform.billing.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.application.InvoiceView;
import com.asp.platform.billing.domain.CustomerSnapshot;
import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.FiscalAddress;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoiceLine;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.InvoiceTotals;
import com.asp.platform.billing.domain.IssuedDocument;
import com.asp.platform.billing.domain.PaymentProgress;
import com.asp.platform.billing.domain.Settlement;

/// An invoice or credit note as the screens read it (docs/api/release-4.md "Invoice resource").
record InvoiceResponse(UUID id, DocumentKind kind, InvoiceStatus status, UUID workOrderId, int workOrderNumber,
        @Nullable UUID creditOfId, @Nullable String creditOfNumber, @Nullable String number, @Nullable String series,
        @Nullable Integer sequence, @Nullable String atcud, @Nullable String qrPayload, @Nullable String hashFragment,
        @Nullable Instant issuedAt, String provider, boolean sandbox, IssuerResponse issuer,
        CustomerResponse customer, @Nullable String plate, List<LineResponse> lines, List<VatRow> vatSummary,
        long netCents, long vatCents, long totalCents, long paidCents, long pendingCents, long balanceCents,
        long overpaidCents, long refundDueCents, @Nullable PaymentProgress paymentStatus,
        List<PaymentResponse> payments, @Nullable CreditNoteResponse creditNote, @Nullable String reason,
        @Nullable Instant closedAt, @Nullable FailureResponse failure, Instant createdAt, long version) {

    record IssuerResponse(String name, @Nullable String nif, String country) {
    }

    record CustomerResponse(String name, @Nullable String nif, boolean finalConsumer,
            @Nullable AddressResponse address) {
    }

    record AddressResponse(String street, @Nullable String detail, String postalCode, String locality,
            String country) {
    }

    record LineResponse(UUID id, int position, String type, String description, BigDecimal quantity,
            long unitPriceCents, BigDecimal discountPercent, BigDecimal vatRate, long netCents, long vatCents,
            long totalCents) {
    }

    record VatRow(BigDecimal vatRate, long netCents, long vatCents) {
    }

    record CreditNoteResponse(UUID id, @Nullable String number, InvoiceStatus status) {
    }

    record FailureResponse(String code, String message, int attempts, @Nullable Instant lastAttemptAt) {
    }

    static InvoiceResponse of(InvoiceView view) {
        Invoice invoice = view.invoice();
        IssuedDocument issued = invoice.issued();
        InvoiceTotals totals = invoice.totals();
        Settlement settlement = view.settlement();
        CustomerSnapshot customer = invoice.customer();
        FiscalAddress address = customer.address();
        Invoice note = view.creditNote();
        Invoice.Failure failure = invoice.failure();
        String closedReason = invoice.closedReason();
        return new InvoiceResponse(invoice.id(), invoice.kind(), invoice.status(), invoice.workOrderId(),
                invoice.workOrderNumber(), invoice.creditOfId(), view.creditOfNumber(),
                issued == null ? null : issued.fullNumber(), issued == null ? null : issued.series(),
                issued == null ? null : issued.sequence(), issued == null ? null : issued.atcud(),
                issued == null ? null : issued.qrPayload(), issued == null ? null : issued.hashFragment(),
                issued == null ? null : issued.issuedAt(), invoice.provider(), "sandbox".equals(invoice.provider()),
                new IssuerResponse(invoice.issuer().name(), invoice.issuer().nif(), invoice.issuer().country()),
                new CustomerResponse(customer.name(), customer.nif(), customer.finalConsumer(),
                        address == null ? null : new AddressResponse(address.street(), address.detail(),
                                address.postalCode(), address.locality(), address.country())),
                invoice.plate(), invoice.lines().stream().map(InvoiceResponse::line).toList(),
                totals.vatSummary().stream().map(r -> new VatRow(r.vatRate(), r.netCents(), r.vatCents())).toList(),
                totals.netCents(), totals.vatCents(), totals.totalCents(), settlement.paidCents(),
                settlement.pendingCents(), settlement.balanceCents(), settlement.overpaidCents(),
                settlement.refundDueCents(), settlement.progress(),
                view.payments().stream().map(PaymentResponse::of).toList(),
                note == null ? null : new CreditNoteResponse(note.id(),
                        note.issued() == null ? null : note.issued().fullNumber(), note.status()),
                closedReason != null ? closedReason : invoice.reason(), invoice.closedAt(),
                failure == null ? null : new FailureResponse(failure.code(), failure.message(), invoice.attempts(),
                        invoice.lastAttemptAt()),
                invoice.createdAt(), invoice.version());
    }

    private static LineResponse line(InvoiceLine l) {
        return new LineResponse(l.id(), l.position(), l.kind().name(), l.description(), l.quantity(),
                l.unitPriceCents(), l.discountPercent(), l.vatRate(), l.netCents(), l.vatCents(), l.totalCents());
    }
}
