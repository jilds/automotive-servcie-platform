package com.asp.platform.shop.domain;

import com.asp.platform.shared.InvalidInputException;

/// A product search longer than [#MAX] characters. Mapped to 400 `validation` on `q`.
public class ShopQueryTooLongException extends InvalidInputException {

    public static final int MAX = 60;

    public ShopQueryTooLongException() {
        super("validation", "q", "A search has at most " + MAX + " characters");
    }
}
