package com.asp.platform.billing;

import java.util.UUID;

/// Published after a payment becomes PAID (recorded by staff, confirmed by the provider). No personal data.
///
/// @param method    CASH, CARD, TRANSFER, MULTIBANCO or MBWAY
/// @param fullyPaid true when the invoice is paid in full with this payment
/// @param lateOnClosedInvoice true when the invoice was already voided or credited: money to give back
public record PaymentReceived(UUID tenantId, UUID invoiceId, UUID paymentId, String method, long amountCents,
        boolean fullyPaid, boolean lateOnClosedInvoice) {
}
