package com.asp.platform.reporting.domain;

import com.asp.platform.shared.InvalidInputException;

/// A report would have more rows than the cap (the cost-vs-quote list, JSON or CSV, or the orders a KPI reads). 400 `report-too-many-rows` on `format`: narrow the period.
public class ReportTooManyRowsException extends InvalidInputException {

    public ReportTooManyRowsException(int maxRows) {
        super("report-too-many-rows", "format", "A report has at most " + maxRows + " rows: narrow the period");
    }
}
