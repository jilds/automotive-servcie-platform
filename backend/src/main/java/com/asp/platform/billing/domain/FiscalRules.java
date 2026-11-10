package com.asp.platform.billing.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.PortugueseNif;

/// Who the document is addressed to, and what a document of that size needs to say about them.
public final class FiscalRules {

    private static final String PORTUGAL = "PT";
    private static final java.util.regex.Pattern PT_POSTAL_CODE = java.util.regex.Pattern.compile("[0-9]{4}-[0-9]{3}");

    private FiscalRules() {
    }

    /// @param name    the customer name, never blank
    /// @param nif     the tax number, or null or blank
    /// @param address the postal address, or null
    public record Party(String name, @Nullable String nif, @Nullable PartyAddress address) {
    }

    /// An address as given; the country may be left out, then it is the workshop country (nothing here assumes Portugal).
    public record PartyAddress(String street, @Nullable String detail, String postalCode, String locality,
            @Nullable String country) {
    }

    /// Picks the customer and checks the rules, in this order: a Portuguese NIF must be valid (only when the
    /// customer country is PT: the address country, else the workshop one), then above the final consumer limit the
    /// NIF and the address are required.
    ///
    /// @param finalConsumer      issue to "Consumidor final" without NIF or address, ignoring the other parties
    /// @param override           the customer the request names, replacing the vehicle owner
    /// @param owner              the vehicle owner, when known
    /// @param workshopCountry    ISO code of the workshop, the country of a customer without an address
    /// @param finalConsumerLimit the largest total, in cents, a document may have without NIF and address
    public static CustomerSnapshot customerFor(boolean finalConsumer, @Nullable Party override, @Nullable Party owner,
            String workshopCountry, long totalCents, long finalConsumerLimit) {
        Party party = finalConsumer ? new Party(CustomerSnapshot.FINAL_CONSUMER_NAME, null, null)
                : override != null ? override
                : owner != null ? owner : new Party(CustomerSnapshot.FINAL_CONSUMER_NAME, null, null);
        String nif = party.nif() == null || party.nif().isBlank() ? null : party.nif().strip();
        PartyAddress given = finalConsumer ? null : party.address();
        String country = (given == null || given.country() == null || given.country().isBlank() ? workshopCountry
                : given.country()).strip().toUpperCase(Locale.ROOT);
        FiscalAddress address = given == null ? null : new FiscalAddress(given.street(), given.detail(),
                given.postalCode(), given.locality(), country);
        String name = party.name().strip();
        if (name.length() > CustomerNameTooLongException.MAX_LENGTH) {
            throw new CustomerNameTooLongException();
        }
        if (address != null && PORTUGAL.equals(country) && !PT_POSTAL_CODE.matcher(address.postalCode()).matches()) {
            throw new InvalidQueryException("customer.address.postalCode");
        }
        if (nif != null && !PortugueseNif.isAcceptable(nif, country)) {
            throw new InvalidCustomerNifException();
        }
        if (totalCents > finalConsumerLimit) {
            if (nif == null) {
                throw new CustomerNifRequiredException();
            }
            if (address == null) {
                throw new CustomerAddressRequiredException();
            }
        }
        return new CustomerSnapshot(name, nif, finalConsumer, address);
    }
}
