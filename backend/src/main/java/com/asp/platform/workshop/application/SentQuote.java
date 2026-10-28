package com.asp.platform.workshop.application;

/// A quote just sent, with the raw status token: the only time it is returned (the database keeps its hash).
///
/// @param customerHasEmail the vehicle's customer has an e-mail, so the link is mailed when mail is on
public record SentQuote(QuoteView quote, String statusToken, boolean customerHasEmail) {

    @Override
    public String toString() {
        return "SentQuote[quote=" + quote.quote().id() + ", customerHasEmail=" + customerHasEmail + "]";
    }
}
