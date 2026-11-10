package com.asp.platform.billing.application;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.domain.FiscalRules;

/// The request to invoice a work order.
///
/// @param requestId     the client's idempotency key
/// @param finalConsumer issue to "Consumidor final"
/// @param customer      replaces the vehicle owner, or null
public record IssueRequest(UUID requestId, boolean finalConsumer, FiscalRules.@Nullable Party customer) {
}
