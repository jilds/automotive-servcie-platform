package com.asp.platform.reporting.domain;

/// All the report slots are taken and none was free within the wait. 429 `try-again`: the caller retries shortly.
public class ReportBusyException extends RuntimeException {

    public static final String CODE = "try-again";

    public ReportBusyException() {
        super("Reports are busy, try again in a moment");
    }
}
