package com.asp.platform.stock.domain;

/// A product with its stock level, read together in one query.
public record ProductStock(Product product, StockLevel level) {
}
