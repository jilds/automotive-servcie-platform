package com.asp.platform.billing.application;

import com.asp.platform.billing.domain.InvoiceStatus;

/// What issuing a document came to.
///
/// @param created true when this call stored the document, false when it found the one the same request made
public record IssueOutcome(InvoiceView view, boolean created) {

    public boolean issued() {
        return view.invoice().status() == InvoiceStatus.ISSUED;
    }
}
