package com.asp.platform.stock.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.domain.StockMovement;

/// A ledger row with the name of the person who made it (null when that user no longer exists).
public record MovementView(StockMovement movement, @Nullable String changedByName) {
}
