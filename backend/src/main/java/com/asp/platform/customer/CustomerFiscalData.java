package com.asp.platform.customer;

import org.jspecify.annotations.Nullable;

/// What an invoice copies from the customer who owns a vehicle: name, tax number and postal address. Personal data:
/// for the invoice snapshot only, never logged, never shown to mechanics.
///
/// @param nif     the customer's tax number, or null when they gave none
/// @param address the postal address, or null when there is none
public record CustomerFiscalData(String name, @Nullable String nif, @Nullable FiscalAddress address) {

    /// @param detail  floor or door, or null
    /// @param country ISO 3166-1 alpha-2 code
    public record FiscalAddress(String street, @Nullable String detail, String postalCode, String locality,
            String country) {
    }

    @Override
    public String toString() {
        return "CustomerFiscalData[hasNif=" + (nif != null) + ", hasAddress=" + (address != null) + "]";
    }
}
