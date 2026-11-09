package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// Only the owner may move a work order out of AWAITING_APPROVAL before the latest quote is answered.
public class ApprovalPendingException extends ConflictException {

    public ApprovalPendingException() {
        super("approval-pending", "The customer has not answered the quote yet; only the owner can move the order");
    }
}
