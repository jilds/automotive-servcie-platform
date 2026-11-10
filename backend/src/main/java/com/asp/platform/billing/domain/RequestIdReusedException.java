package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// The request id already belongs to a document that is closed (discarded or voided) or to another work order: a
/// request id is used once, so a new attempt needs a new one. 409 request-id-reused.
public class RequestIdReusedException extends ConflictException {

    public RequestIdReusedException() {
        super("request-id-reused", "This request id was already used; start again with a new one");
    }
}
