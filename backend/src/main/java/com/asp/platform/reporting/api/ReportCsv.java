package com.asp.platform.reporting.api;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.asp.platform.reporting.application.CostVsQuoteReport;
import com.asp.platform.reporting.application.KpiReport;
import com.asp.platform.reporting.application.ProductivityReport;
import com.asp.platform.reporting.domain.Csv;

/// The CSV documents of the three reports (docs/api/release-4.md "CSV"): the JSON field names as columns, money as
/// `…Eur`, ids left out.
final class ReportCsv {

    private ReportCsv() {
    }

    static String costVsQuote(CostVsQuoteReport report) {
        ZoneId zone = ZoneId.of(report.timezone());
        List<List<String>> rows = new ArrayList<>();
        for (CostVsQuoteReport.Item i : report.items()) {
            CostVsQuoteReport.QuoteRef q = i.quote();
            rows.add(List.of(Csv.integer((long) i.number()), Csv.text(i.plate()),
                    Csv.timestamp(i.deliveredAt(), zone), Csv.text(i.mechanicName()),
                    q == null ? "" : Csv.integer((long) q.number()), q == null ? "" : Csv.euros(q.totalCents()),
                    Csv.euros(i.finalNetCents()), Csv.euros(i.finalVatCents()), Csv.euros(i.finalTotalCents()),
                    Csv.euros(i.differenceCents()), Csv.decimal(i.differencePercent()),
                    Csv.decimal(i.standardHours()), Csv.decimal(i.trackedHours()), Csv.euros(i.partsCostCents()),
                    Csv.euros(i.labourCostCents()), Csv.euros(i.marginCents()), Csv.decimal(i.marginPercent())));
        }
        return Csv.document(List.of("number", "plate", "deliveredAt", "mechanic", "quoteNumber", "quoteTotalEur",
                "finalNetEur", "finalVatEur", "finalTotalEur", "differenceEur", "differencePercent", "standardHours",
                "trackedHours", "partsCostEur", "labourCostEur", "marginEur", "marginPercent"), rows);
    }

    static String productivity(ProductivityReport report) {
        List<List<String>> rows = new ArrayList<>();
        for (ProductivityReport.MechanicRow m : report.mechanics()) {
            rows.add(List.of(Csv.text(m.name()), Csv.decimal(m.trackedHours()), Csv.integer(m.jobs()),
                    Csv.decimal(m.standardHours()), Csv.decimal(m.trackedHoursOnJobs()),
                    Csv.decimal(m.efficiency()), Csv.decimal(m.averageHoursInWorkshop())));
        }
        return Csv.document(List.of("mechanic", "trackedHours", "jobs", "standardHours", "trackedHoursOnJobs",
                "efficiency", "averageHoursInWorkshop"), rows);
    }

    static String kpis(KpiReport report) {
        Rows rows = new Rows();
        KpiReport.AverageTimePerJob time = report.averageTimePerJob();
        rows.add("averageTimePerJob", "", "trackedHours", Csv.decimal(time.trackedHours()));
        rows.add("averageTimePerJob", "", "ordersWithTime", Csv.integer(time.ordersWithTime()));
        rows.add("averageTimePerJob", "", "hoursPerJob", Csv.decimal(time.hoursPerJob()));
        for (KpiReport.ServiceTime s : time.byService()) {
            rows.add("averageTimePerJob", s.name(), "trackedHours", Csv.decimal(s.trackedHours()));
            rows.add("averageTimePerJob", s.name(), "orders", Csv.integer(s.orders()));
            rows.add("averageTimePerJob", s.name(), "hoursPerJob", Csv.decimal(s.hoursPerJob()));
        }

        KpiReport.AverageOrderValue value = report.averageOrderValue();
        rows.add("averageOrderValue", "", "source", Csv.text(value.source()));
        rows.add("averageOrderValue", "", "invoicedOrders", Csv.integer(value.invoicedOrders()));
        rows.add("averageOrderValue", "", "creditedOrders", Csv.integer(value.creditedOrders()));
        rows.add("averageOrderValue", "", "netEur", Csv.euros(value.netCents()));
        rows.add("averageOrderValue", "", "totalEur", Csv.euros(value.totalCents()));
        rows.add("averageOrderValue", "", "averageNetEur", Csv.euros(value.averageNetCents()));
        rows.add("averageOrderValue", "", "averageTotalEur", Csv.euros(value.averageTotalCents()));
        rows.add("notYetInvoiced", "", "orders", Csv.integer(value.notYetInvoiced().orders()));
        rows.add("notYetInvoiced", "", "netEur", Csv.euros(value.notYetInvoiced().netCents()));
        rows.add("notYetInvoiced", "", "totalEur", Csv.euros(value.notYetInvoiced().totalCents()));

        KpiReport.Margin margin = report.margin();
        rows.add("margin", "", "basis", Csv.text(margin.basis()));
        rows.add("margin", "", "orders", Csv.integer(margin.orders()));
        rows.add("margin", "", "netEur", Csv.euros(margin.netCents()));
        rows.add("margin", "", "partsCostEur", Csv.euros(margin.partsCostCents()));
        rows.add("margin", "", "labourCostEur", Csv.euros(margin.labourCostCents()));
        rows.add("margin", "", "marginEur", Csv.euros(margin.marginCents()));
        rows.add("margin", "", "marginPercent", Csv.decimal(margin.marginPercent()));
        for (KpiReport.ServiceMargin s : margin.byService()) {
            rows.add("margin", s.name(), "lines", Csv.integer(s.lines()));
            rows.add("margin", s.name(), "orders", Csv.integer(s.orders()));
            rows.add("margin", s.name(), "netEur", Csv.euros(s.netCents()));
            rows.add("margin", s.name(), "trackedHours", Csv.decimal(s.trackedHours()));
            rows.add("margin", s.name(), "labourCostEur", Csv.euros(s.labourCostCents()));
            rows.add("margin", s.name(), "marginEur", Csv.euros(s.marginCents()));
            rows.add("margin", s.name(), "marginPercent", Csv.decimal(s.marginPercent()));
        }

        KpiReport.PackageParts packageParts = margin.packageParts();
        rows.add("packageParts", "", "lines", Csv.integer(packageParts.lines()));
        rows.add("packageParts", "", "netEur", Csv.euros(packageParts.netCents()));
        rows.add("packageParts", "", "partsCostEur", Csv.euros(packageParts.partsCostCents()));
        rows.add("packageParts", "", "marginEur", Csv.euros(packageParts.marginCents()));
        rows.add("packageParts", "", "marginPercent", Csv.decimal(packageParts.marginPercent()));
        KpiReport.OtherParts otherParts = margin.otherParts();
        rows.add("otherParts", "", "netEur", Csv.euros(otherParts.netCents()));
        rows.add("otherParts", "", "partsCostEur", Csv.euros(otherParts.partsCostCents()));
        rows.add("otherParts", "", "marginEur", Csv.euros(otherParts.marginCents()));
        rows.add("otherParts", "", "marginPercent", Csv.decimal(otherParts.marginPercent()));

        KpiReport.Retention retention = report.retention();
        rows.add("retention", "", "months", Csv.integer((long) retention.months()));
        rows.add("retention", "", "customers", Csv.integer(retention.customers()));
        rows.add("retention", "", "retained", Csv.integer(retention.retained()));
        rows.add("retention", "", "ratePercent", Csv.decimal(retention.ratePercent()));
        rows.add("retention", "", "final", Boolean.toString(retention.complete()));
        rows.add("retention", "", "monthsCovered", Csv.integer((long) retention.monthsCovered()));
        KpiReport.Comebacks comebacks = report.comebacks();
        rows.add("comebacks", "", "days", Csv.integer((long) comebacks.days()));
        rows.add("comebacks", "", "orders", Csv.integer(comebacks.orders()));
        rows.add("comebacks", "", "ratePercent", Csv.decimal(comebacks.ratePercent()));

        unavailable(rows, "occupancy", report.occupancy());
        unavailable(rows, "satisfaction", report.satisfaction());
        unavailable(rows, "complaints", report.complaints());
        return Csv.document(List.of("kpi", "item", "metric", "value"), rows.list);
    }

    private static void unavailable(Rows rows, String kpi, KpiReport.Unavailable value) {
        rows.add(kpi, "", "available", Boolean.toString(value.available()));
        rows.add(kpi, "", "availableWith", Csv.text(value.availableWith()));
    }

    /// `kpi;item;metric;value` rows: the item (a service name) is user text, so it goes through [Csv#text].
    private static final class Rows {

        private final List<List<String>> list = new ArrayList<>();

        void add(String kpi, @Nullable String item, String metric, String value) {
            list.add(List.of(kpi, Csv.text(item), metric, value));
        }
    }

}
