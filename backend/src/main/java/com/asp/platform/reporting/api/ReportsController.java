package com.asp.platform.reporting.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.reporting.application.CostVsQuoteReport;
import com.asp.platform.reporting.application.KpiReport;
import com.asp.platform.reporting.application.ProductivityReport;
import com.asp.platform.reporting.application.ReportBulkhead;
import com.asp.platform.reporting.application.ReportService;
import com.asp.platform.reporting.domain.InvalidReportParameterException;
import com.asp.platform.shared.CurrentUser;

/// The owner's reports (docs/api/release-4.md "Reports"): prices, margins and colleagues' hours, so OWNER only.
/// Parameters are read as text and parsed here, so every bad value is a 400 `validation` on its own parameter.
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('OWNER')")
class ReportsController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reports;
    private final ReportBulkhead bulkhead;

    ReportsController(ReportService reports, ReportBulkhead bulkhead) {
        this.reports = reports;
        this.bulkhead = bulkhead;
    }

    @GetMapping("/cost-vs-quote")
    ResponseEntity<?> costVsQuote(CurrentUser user, @RequestParam(required = false) @Nullable String from,
            @RequestParam(required = false) @Nullable String to,
            @RequestParam(required = false) @Nullable String mechanicId,
            @RequestParam(required = false) @Nullable String hasApprovedQuote,
            @RequestParam(required = false) @Nullable String page, @RequestParam(required = false) @Nullable String size,
            @RequestParam(required = false) @Nullable String format) {
        boolean csv = isCsv(format);
        int pageNumber = integer(page, "page", 0, 0, Integer.MAX_VALUE);
        int pageSize = integer(size, "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        @Nullable UUID mechanic = uuid(mechanicId, "mechanicId");
        @Nullable Boolean approved = bool(hasApprovedQuote, "hasApprovedQuote");
        @Nullable LocalDate fromDate = date(from, "from");
        @Nullable LocalDate toDate = date(to, "to");
        if (csv) {
            return bulkhead.run(user.tenantId(), () -> {
                CostVsQuoteReport report = reports.costVsQuoteForCsv(user.tenantId(), fromDate, toDate, mechanic,
                        approved);
                return csv("cost-vs-quote", report.from(), report.to(), ReportCsv.costVsQuote(report));
            });
        }
        return bulkhead.run(user.tenantId(), () -> ResponseEntity.ok(reports.costVsQuote(user.tenantId(), fromDate, toDate, mechanic,
                approved, pageNumber, pageSize)));
    }

    @GetMapping("/productivity")
    ResponseEntity<?> productivity(CurrentUser user, @RequestParam(required = false) @Nullable String from,
            @RequestParam(required = false) @Nullable String to,
            @RequestParam(required = false) @Nullable String mechanicId,
            @RequestParam(required = false) @Nullable String format) {
        boolean csv = isCsv(format);
        @Nullable UUID mechanic = uuid(mechanicId, "mechanicId");
        @Nullable LocalDate fromDate = date(from, "from");
        @Nullable LocalDate toDate = date(to, "to");
        return bulkhead.run(user.tenantId(), () -> {
            ProductivityReport report = reports.productivity(user.tenantId(), fromDate, toDate, mechanic);
            return csv ? csv("productivity", report.from(), report.to(), ReportCsv.productivity(report))
                    : ResponseEntity.ok(report);
        });
    }

    @GetMapping("/kpis")
    ResponseEntity<?> kpis(CurrentUser user, @RequestParam(required = false) @Nullable String from,
            @RequestParam(required = false) @Nullable String to,
            @RequestParam(required = false) @Nullable String format) {
        boolean csv = isCsv(format);
        @Nullable LocalDate fromDate = date(from, "from");
        @Nullable LocalDate toDate = date(to, "to");
        return bulkhead.run(user.tenantId(), () -> {
            KpiReport report = reports.kpis(user.tenantId(), fromDate, toDate);
            return csv ? csv("kpis", report.from(), report.to(), ReportCsv.kpis(report)) : ResponseEntity.ok(report);
        });
    }

    private static ResponseEntity<byte[]> csv(String report, LocalDate from, LocalDate to, String document) {
        byte[] body = document.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("%s-%s-%s.csv".formatted(report, from, to)).build().toString())
                .body(body);
    }

    /// `json` (the default) or `csv`.
    private static boolean isCsv(@Nullable String format) {
        if (format == null || format.equals("json")) {
            return false;
        }
        if (format.equals("csv")) {
            return true;
        }
        throw new InvalidReportParameterException("format");
    }

    private static @Nullable LocalDate date(@Nullable String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidReportParameterException(parameter);
        }
    }

    private static @Nullable UUID uuid(@Nullable String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidReportParameterException(parameter);
        }
    }

    private static @Nullable Boolean bool(@Nullable String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new InvalidReportParameterException(parameter);
        };
    }

    private static int integer(@Nullable String value, String parameter, int fallback, int min, int max) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) {
                throw new InvalidReportParameterException(parameter);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new InvalidReportParameterException(parameter);
        }
    }
}
