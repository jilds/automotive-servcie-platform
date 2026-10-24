package com.asp.platform.stock.domain;

import java.util.UUID;

/// The document a movement belongs to, such as `WORK_ORDER` with its id or `PURCHASE_ORDER` with its id.
public record StockReference(String type, UUID id) {

    public static final String PURCHASE_ORDER = "PURCHASE_ORDER";
}
