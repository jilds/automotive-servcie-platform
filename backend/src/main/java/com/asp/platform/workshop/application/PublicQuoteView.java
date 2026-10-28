package com.asp.platform.workshop.application;

import java.time.ZoneId;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteStatus;

/// A quote as the customer sees it on the link: no customer data and no ids.
///
/// @param status   EXPIRED for a SENT quote past `validUntil`
/// @param vehicle  null when the vehicle is not found
/// @param timeZone the workshop's, in which the page shows times
public record PublicQuoteView(Quote quote, QuoteStatus status, int workOrderNumber, @Nullable Vehicle vehicle,
        String workshopName, ZoneId timeZone) {

    /// @param plateEra numbering series such as "PT_2020", or null when unknown
    /// @param label    "Renault Clio", or empty
    public record Vehicle(String plateDisplay, @Nullable String plateEra, String registrationCountry, String label) {
    }

    /// True while the customer may approve or reject.
    public boolean canDecide() {
        return status == QuoteStatus.SENT;
    }
}
