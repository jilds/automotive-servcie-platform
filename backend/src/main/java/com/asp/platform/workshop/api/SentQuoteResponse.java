package com.asp.platform.workshop.api;

import com.asp.platform.workshop.application.SentQuote;

/// The quote just sent and its status token, returned only here: the screen builds the link
/// `<origin>/w/{slug}/quote/{statusToken}`.
///
/// @param customerHasEmail the vehicle's customer has an e-mail, so the link is mailed when mail is on
record SentQuoteResponse(QuoteResponse quote, String statusToken, boolean customerHasEmail) {

    static SentQuoteResponse of(SentQuote sent) {
        return new SentQuoteResponse(QuoteResponse.of(sent.quote()), sent.statusToken(), sent.customerHasEmail());
    }

    @Override
    public String toString() {
        return "SentQuoteResponse[quote=" + quote.id() + "]";
    }
}
