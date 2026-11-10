package com.asp.platform.billing.domain;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// What a provider is asked to issue. The provider must be idempotent on `externalRef`: asking again for the same
/// reference returns the document it already issued.
///
/// @param externalRef     the id of our document
/// @param credited        for a credit note, the invoice it credits
/// @param reason          for a credit note, why
public record DocumentDraft(UUID externalRef, DocumentKind kind, IssuerSnapshot issuer, CustomerSnapshot customer,
        List<InvoiceLine> lines, InvoiceTotals totals, @Nullable String plate, @Nullable Credited credited,
        @Nullable String reason) {

    /// @param providerDocumentId the provider's id of the credited invoice
    /// @param fullNumber         its number
    public record Credited(String providerDocumentId, String fullNumber) {
    }
}
