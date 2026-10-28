package com.asp.platform.customer;

import org.jspecify.annotations.Nullable;

/// How to reach the customer who owns a vehicle, for a message to them (the quote mail) only. Never shown to
/// mechanics, never logged.
///
/// @param email        the customer's e-mail, or null when they gave none
/// @param plateDisplay the vehicle's plate as shown (see [VehicleSummary#plateDisplay])
public record CustomerContact(String name, @Nullable String email, String plateDisplay) {

    @Override
    public String toString() {
        return "CustomerContact[hasEmail=" + (email != null) + "]";
    }
}
