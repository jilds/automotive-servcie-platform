package com.asp.platform.stock.domain;

import com.asp.platform.shared.InvalidInputException;

/// A stock adjustment must say why (count, breakage, loss). Mapped to 400 `adjustment-reason-required` on `reason`.
public class AdjustmentReasonRequiredException extends InvalidInputException {

    public AdjustmentReasonRequiredException() {
        super("adjustment-reason-required", "reason", "Say why the stock is adjusted");
    }
}
