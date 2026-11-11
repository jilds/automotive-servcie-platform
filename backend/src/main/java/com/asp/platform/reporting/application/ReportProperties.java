package com.asp.platform.reporting.application;

import java.time.Duration;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// `app.reports.*`.
///
/// @param csvMaxRows    the most rows the cost-vs-quote list may have, for JSON and CSV alike (400
///                      `report-too-many-rows` above it; the query reads one more row to know); 24 months of a busy
///                      workshop is about 5 000 orders
/// @param maxConcurrent how many reports may run at once on this instance
/// @param maxConcurrentPerWorkshop how many of those one workshop may have running (above it: 429 `try-again` at once)
/// @param maxWait        how long a report waits for a free slot before answering 429 `try-again`
@Validated
@ConfigurationProperties("app.reports")
public record ReportProperties(@DefaultValue("10000") @Min(1) int csvMaxRows,
        @DefaultValue("3") @Min(1) int maxConcurrent,
        @DefaultValue("2") @Min(1) int maxConcurrentPerWorkshop, @DefaultValue("2s") Duration maxWait) {
}
