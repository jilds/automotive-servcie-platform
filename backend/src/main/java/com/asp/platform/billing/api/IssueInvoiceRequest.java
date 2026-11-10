package com.asp.platform.billing.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.application.IssueRequest;
import com.asp.platform.billing.domain.FiscalRules;
import com.asp.platform.shared.IsoCountry;
import com.asp.platform.shared.OneLineName;

/// `POST /work-orders/{id}/invoices`. See docs/api/release-4.md "IssueInvoiceRequest".
record IssueInvoiceRequest(@NotNull UUID requestId, @Nullable Boolean finalConsumer,
        @Valid @Nullable CustomerRequest customer) {

    record CustomerRequest(@NotBlank @OneLineName String name, @Size(max = 20) @Nullable String nif,
            @Valid @Nullable AddressRequest address) {
    }

    record AddressRequest(@NotBlank @Size(max = 200) String street, @Size(max = 100) @Nullable String detail,
            @NotBlank @Size(max = 20) String postalCode, @NotBlank @Size(max = 100) String locality,
            @IsoCountry @Nullable String country) {
    }

    /// The country of an address that does not give one stays null: the workshop country is applied where the invoice
    /// is made (FiscalRules), so nothing here assumes Portugal.
    IssueRequest toRequest() {
        FiscalRules.Party party = null;
        if (customer != null) {
            FiscalRules.PartyAddress address = null;
            AddressRequest a = customer.address();
            if (a != null) {
                address = new FiscalRules.PartyAddress(a.street().strip(),
                        a.detail() == null || a.detail().isBlank() ? null : a.detail().strip(), a.postalCode().strip(),
                        a.locality().strip(), a.country() == null || a.country().isBlank() ? null : a.country().strip());
            }
            party = new FiscalRules.Party(customer.name(), customer.nif(), address);
        }
        return new IssueRequest(requestId, Boolean.TRUE.equals(finalConsumer), party);
    }
}
