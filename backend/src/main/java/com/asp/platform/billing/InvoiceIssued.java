package com.asp.platform.billing;

import java.util.UUID;

/// Published after an invoice or credit note is issued by the provider. No personal data. Nothing listens yet (the
/// e-mail of the invoice and the reports come later).
///
/// @param number the full number the provider assigned, for example "FT SANDBOX/1"
public record InvoiceIssued(UUID tenantId, UUID invoiceId, UUID workOrderId, String number, long totalCents) {
}
