package com.asp.platform.reporting.domain;

import com.asp.platform.shared.InvalidInputException;

/// The period is longer than [ReportPeriod#MAX_MONTHS] months. 400 `report-range-too-large` on `to`.
public class ReportRangeTooLargeException extends InvalidInputException {

    public ReportRangeTooLargeException() {
        super("report-range-too-large", "to",
                "A report covers at most " + ReportPeriod.MAX_MONTHS + " months");
    }
}
