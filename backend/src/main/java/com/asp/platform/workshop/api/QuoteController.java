package com.asp.platform.workshop.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.QuoteService;

/// Quotes of a work order. Owner and front desk only: quotes carry prices, which mechanics never get (they see the
/// latest quote's status on the work order and the board).
@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/quotes")
class QuoteController {

    private final QuoteService quotes;

    QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    /// No body. 409 `work-order-not-quotable` outside DIAGNOSTICS / AWAITING_APPROVAL or without lines.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    SentQuoteResponse send(CurrentUser user, @PathVariable UUID workOrderId) {
        return SentQuoteResponse.of(quotes.send(user, workOrderId));
    }

    /// Newest first, with lines.
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<QuoteResponse> list(CurrentUser user, @PathVariable UUID workOrderId) {
        return quotes.list(user.tenantId(), workOrderId).stream().map(QuoteResponse::of).toList();
    }

    /// The customer's decision, given by phone or at the counter, with a note; channel STAFF.
    @PostMapping("/{quoteId}/decision")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    QuoteResponse decide(CurrentUser user, @PathVariable UUID workOrderId, @PathVariable UUID quoteId,
            @Valid @RequestBody DecisionRequest request) {
        return QuoteResponse.of(quotes.decide(user, workOrderId, quoteId, request.toDecision(), request.note(),
                request.version()));
    }
}
