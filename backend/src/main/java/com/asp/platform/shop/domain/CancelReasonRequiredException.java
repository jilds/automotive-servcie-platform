package com.asp.platform.shop.domain;

import com.asp.platform.shared.InvalidInputException;

/// The front desk cancels an order only with a reason, which the customer is told. Mapped to 400
/// `cancel-reason-required` on `reason`.
public class CancelReasonRequiredException extends InvalidInputException {

    public static final String CODE = "cancel-reason-required";

    public CancelReasonRequiredException() {
        super(CODE, "reason", "Say why the order is cancelled");
    }
}
