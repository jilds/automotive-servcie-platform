package com.asp.platform.identity.domain;

/// The add-ons the platform admin switched on for a workshop, independent of its plan (ADR 0004).
///
/// @param booking public online booking of drop-off slots
/// @param shop    public online shop, paid at the counter
public record AddOns(boolean booking, boolean shop) {

    private static final AddOns NONE = new AddOns(false, false);

    /// Every add-on off: how a new workshop starts.
    public static AddOns none() {
        return NONE;
    }
}
