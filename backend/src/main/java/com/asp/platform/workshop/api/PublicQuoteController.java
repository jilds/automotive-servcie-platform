package com.asp.platform.workshop.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.OneLineName;
import com.asp.platform.shared.PublicWorkshopRef;
import com.asp.platform.workshop.application.QuoteService;
import com.asp.platform.workshop.domain.Quote;

/// The customer's quote link on the workshop's public pages (ADR 0004): anonymous, served for the workshop of the
/// slug. A core feature, so no add-on check. An unknown token, or one of another workshop, answers 404
/// `quote-not-found`.
@RestController
@RequestMapping("/api/v1/public/{slug}/quotes/{token}")
class PublicQuoteController {

    private final QuoteService quotes;

    PublicQuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    /// `{ "name": "Ana Silva", "accepted": true }`: the name the customer signs with (one line, no links) and the
    /// acceptance of the quote.
    record ApproveRequest(@NotBlank @Size(max = Quote.MAX_NAME) @OneLineName String name,
            @Nullable Boolean accepted) {

        /// Reported as `errors.accepted`.
        @AssertTrue(message = "Accept the quote")
        boolean isAccepted() {
            return Boolean.TRUE.equals(accepted);
        }
    }

    /// `{ "reason": "…" }`, optional. Several lines and tabs are fine; other control characters (C0, DEL, C1) and the
    /// Unicode line and paragraph separators are refused (400 `validation`, `errors.reason`), as the reason is shown
    /// to the front desk.
    record RejectRequest(@Nullable @Size(max = Quote.MAX_NOTE) @Pattern(regexp = PLAIN_TEXT,
            message = "No control characters") String reason) {
    }

    /// Any text but control characters other than tab and line feed, and U+2028 / U+2029.
    private static final String PLAIN_TEXT = "[^\\x00-\\x08\\x0B-\\x1F\\x7F-\\x9F\\u2028\\u2029]*";

    @GetMapping
    PublicQuoteResponse get(PublicWorkshopRef workshop, @PathVariable String token) {
        return PublicQuoteResponse.of(quotes.publicView(token, workshop.name(), workshop.timeZone()));
    }

    /// 409 `quote-not-open` once decided, superseded or expired; `quote-expired` past its validity.
    @PostMapping("/approve")
    PublicQuoteResponse approve(PublicWorkshopRef workshop, @PathVariable String token,
            @Valid @RequestBody ApproveRequest request) {
        return PublicQuoteResponse.of(quotes.approve(token, request.name(), workshop.name(), workshop.timeZone()));
    }

    /// 409 `quote-not-open` once decided, superseded or expired; `quote-expired` past its validity.
    @PostMapping("/reject")
    PublicQuoteResponse reject(PublicWorkshopRef workshop, @PathVariable String token,
            @Valid @RequestBody(required = false) @Nullable RejectRequest request) {
        return PublicQuoteResponse.of(quotes.reject(token, request == null ? null : request.reason(),
                workshop.name(), workshop.timeZone()));
    }
}
