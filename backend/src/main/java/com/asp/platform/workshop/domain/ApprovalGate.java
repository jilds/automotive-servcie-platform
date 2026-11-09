package com.asp.platform.workshop.domain;

import org.jspecify.annotations.Nullable;

/// The one place for the rule "only the owner leaves AWAITING_APPROVAL before the latest quote is answered".
public final class ApprovalGate {

    private ApprovalGate() {
    }

    /// True when a non-owner may not move an order of this status by hand: it is AWAITING_APPROVAL, the caller is
    /// not the owner and the latest quote (null = none) is not APPROVED or REJECTED.
    public static boolean blocks(WorkOrderStatus status, boolean owner, @Nullable QuoteStatus latestQuote) {
        return status == WorkOrderStatus.AWAITING_APPROVAL && !owner
                && (latestQuote == null || !latestQuote.isAnswered());
    }
}
