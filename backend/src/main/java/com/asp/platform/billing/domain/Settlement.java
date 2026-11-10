package com.asp.platform.billing.domain;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// How much of an invoice is paid, pending and still due, from its payments.
///
/// @param progress  null unless the document is an issued invoice
/// @param refundDueCents what was paid on a credited or voided invoice, owed back
public record Settlement(long paidCents, long pendingCents, long balanceCents, long overpaidCents, long refundDueCents,
        @Nullable PaymentProgress progress) {

    /// @param credited true when an ISSUED credit note credits the invoice
    public static Settlement of(Invoice invoice, List<Payment> payments, boolean credited) {
        long paid = sum(payments, PaymentStatus.PAID);
        long pending = sum(payments, PaymentStatus.PENDING);
        if (!invoice.isIssuedInvoice()) {
            // Money received on a voided invoice (it arrived late) is owed back.
            return new Settlement(paid, pending, 0, 0, invoice.status() == InvoiceStatus.VOIDED ? paid : 0, null);
        }
        long total = invoice.totalCents();
        if (credited) {
            return new Settlement(paid, 0, 0, 0, paid, PaymentProgress.CREDITED);
        }
        PaymentProgress progress = paid >= total ? PaymentProgress.PAID
                : paid > 0 ? PaymentProgress.PARTIALLY_PAID : PaymentProgress.UNPAID;
        return new Settlement(paid, pending, Math.max(0, total - paid), Math.max(0, paid - total), 0, progress);
    }

    /// What can still be asked for: the total minus what is paid or promised.
    public static long left(long totalCents, List<Payment> payments) {
        return Math.max(0, totalCents - sum(payments, PaymentStatus.PAID) - sum(payments, PaymentStatus.PENDING));
    }

    private static long sum(List<Payment> payments, PaymentStatus status) {
        return payments.stream().filter(p -> p.status() == status).mapToLong(Payment::amountCents).sum();
    }
}
