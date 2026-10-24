package com.asp.platform.stock.domain;

/// Why the quantity on hand changed.
public enum MovementType {
    /// Parts came in, for example with a received purchase order. Always adds.
    RECEIPT,
    /// Parts went out, for example on a delivered work order. Always takes away.
    ISSUE,
    /// A correction after counting, a breakage or a loss. Either way; always with a reason.
    ADJUSTMENT
}
