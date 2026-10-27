package com.asp.platform.shop.domain;

import com.asp.platform.shared.InvalidInputException;

/// The front desk asked for orders over more than [#MAX_DAYS] days. Mapped to 400 `validation` on `to`.
public class ShopRangeTooLongException extends InvalidInputException {

    /// Longest period the order list covers, from `from` to `to`.
    public static final int MAX_DAYS = 124;

    public ShopRangeTooLongException() {
        super("validation", "to", "The period may cover at most " + MAX_DAYS + " days");
    }
}
