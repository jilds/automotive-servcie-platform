package com.asp.platform.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Cost vs quote per delivered work order (docs/api/release-4.md "Reports"). The records are the JSON of
/// `GET /api/v1/reports/cost-vs-quote`.
///
/// @param labourCostSet whether the workshop set its labour cost per hour; without it the margin is "parts only"
/// @param marginBasis   `PARTS_AND_LABOUR` or `PARTS_ONLY`
public record CostVsQuoteReport(LocalDate from, LocalDate to, String timezone, boolean labourCostSet,
        String marginBasis, Summary summary, int page, int size, long totalElements, int totalPages,
        List<Item> items) {

    public static final String PARTS_AND_LABOUR = "PARTS_AND_LABOUR";
    public static final String PARTS_ONLY = "PARTS_ONLY";

    /// Over every order that matches the filters. The quote and difference figures cover the orders with an approved
    /// quote only; the rest cover all of them.
    public record Summary(long orders, long ordersWithApprovedQuote, long quoteTotalCents, long finalTotalCents,
            long differenceCents, @Nullable BigDecimal differencePercent, BigDecimal standardHours,
            BigDecimal trackedHours, long netCents, long partsCostCents, @Nullable Long labourCostCents,
            long marginCents, @Nullable BigDecimal marginPercent) {
    }

    /// @param quote the approved quote (highest number), or null
    public record Item(UUID workOrderId, int number, String plate, Instant deliveredAt, @Nullable UUID mechanicId,
            @Nullable String mechanicName, @Nullable QuoteRef quote, long finalNetCents, long finalVatCents,
            long finalTotalCents, @Nullable Long differenceCents, @Nullable BigDecimal differencePercent,
            BigDecimal standardHours, BigDecimal trackedHours, long partsCostCents, @Nullable Long labourCostCents,
            long marginCents, @Nullable BigDecimal marginPercent) {
    }

    /// @param channel `CUSTOMER` or `STAFF`
    public record QuoteRef(UUID id, int number, Instant approvedAt, String channel, long netCents, long totalCents) {
    }
}
