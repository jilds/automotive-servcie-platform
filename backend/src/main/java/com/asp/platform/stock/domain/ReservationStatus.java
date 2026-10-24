package com.asp.platform.stock.domain;

/// A reservation holds stock while it is ACTIVE; it ends either way, never both.
public enum ReservationStatus {
    /// Holds the quantity: it no longer counts as available.
    ACTIVE,
    /// The parts went out (work order delivered); an ISSUE movement took them off the quantity on hand.
    CONSUMED,
    /// The parts were given back (line removed, order cancelled).
    RELEASED
}
