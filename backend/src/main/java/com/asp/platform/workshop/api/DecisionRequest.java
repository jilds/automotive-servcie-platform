package com.asp.platform.workshop.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteDecision;

/// `POST /work-orders/{id}/quotes/{quoteId}/decision`: the customer's decision as the front desk records it. A blank
/// note answers 400 `decision-note-required` (checked by the quote); another decision or a longer note, 400
/// `validation`.
record DecisionRequest(
        @NotNull @Pattern(regexp = "APPROVED|REJECTED", message = "APPROVED or REJECTED") String decision,
        @Nullable @Size(max = Quote.MAX_NOTE) String note,
        @NotNull Long version) {

    /// Call after validation.
    QuoteDecision toDecision() {
        return QuoteDecision.valueOf(decision);
    }
}
