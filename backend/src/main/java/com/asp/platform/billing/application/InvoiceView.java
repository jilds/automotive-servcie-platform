package com.asp.platform.billing.application;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.Settlement;

/// A document with what the screens show next to it.
///
/// @param creditNote   the invoice's credit note that is PENDING, ISSUED or FAILED, if any
/// @param creditOfNumber for a credit note, the number of the invoice it credits
public record InvoiceView(Invoice invoice, List<Payment> payments, @Nullable Invoice creditNote,
        @Nullable String creditOfNumber, Settlement settlement) {
}
