package com.asp.platform.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.reporting.application.ReportQueries.ApprovedQuote;
import com.asp.platform.reporting.application.ReportQueries.DeliveredOrder;
import com.asp.platform.reporting.application.ReportQueries.InvoicedOrder;
import com.asp.platform.reporting.application.ReportQueries.MechanicActivity;
import com.asp.platform.reporting.application.ReportQueries.PartLines;
import com.asp.platform.reporting.application.ReportQueries.ServiceTime;
import com.asp.platform.reporting.application.ReportQueries.WorkshopContext;
import com.asp.platform.reporting.domain.Ratios;
import com.asp.platform.reporting.domain.ReportPeriod;
import com.asp.platform.reporting.domain.ReportTooManyRowsException;

/// The owner's reports. Every method is one read-only transaction over plain SQL, REPEATABLE READ so that all the
/// statements of a report (orders, invoices, retention, ...) read one snapshot and agree with each other. The queries
/// give raw figures and this class does the rounding and the ratios, in one place ([Ratios]). The period is worked out
/// here, in the same transaction, from the workshop's zone (one read of the workshop).
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = ReportService.TIMEOUT_SECONDS)
public class ReportService {

    /// A report that runs longer than this is cancelled (the statements are bounded by the transaction timeout).
    static final int TIMEOUT_SECONDS = 30;
    /// Comebacks are measured over this many 24-hour days.
    public static final int COMEBACK_DAYS = 30;
    /// The KPIs read the delivered orders of the period: more than this many in 24 months is refused (narrow the period).
    static final int KPI_MAX_ORDERS = 100_000;
    /// A per-service list is cut here (a workshop has tens of services).
    private static final int MAX_SERVICES = 200;

    private final ReportQueries queries;
    private final ReportProperties properties;
    private final Clock clock;

    ReportService(ReportQueries queries, ReportProperties properties, Clock clock) {
        this.queries = queries;
        this.properties = properties;
        this.clock = clock;
    }

    /// One page of the cost-vs-quote list, with the summary of the whole filtered set.
    ///
    /// @param from first day or null (defaults in [ReportPeriod#of])
    /// @throws ReportTooManyRowsException when more orders match than `app.reports.csv-max-rows`
    public CostVsQuoteReport costVsQuote(UUID tenantId, @Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable UUID mechanicId, @Nullable Boolean hasApprovedQuote, int page, int size) {
        return costVsQuote(tenantId, from, to, mechanicId, hasApprovedQuote, page, size, false);
    }

    /// Every row, for the CSV.
    ///
    /// @throws ReportTooManyRowsException when there are more rows than `app.reports.csv-max-rows`
    public CostVsQuoteReport costVsQuoteForCsv(UUID tenantId, @Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable UUID mechanicId, @Nullable Boolean hasApprovedQuote) {
        return costVsQuote(tenantId, from, to, mechanicId, hasApprovedQuote, 0, 0, true);
    }

    private CostVsQuoteReport costVsQuote(UUID tenantId, @Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable UUID mechanicId, @Nullable Boolean hasApprovedQuote, int page, int size, boolean all) {
        WorkshopContext workshop = queries.workshop(tenantId);
        ReportPeriod period = period(workshop, from, to);
        Long rate = workshop.labourCostPerHourCents();
        int cap = properties.csvMaxRows();
        List<DeliveredOrder> orders = queries.deliveredOrders(tenantId, period, mechanicId, hasApprovedQuote,
                cap + 1);
        if (orders.size() > cap) {
            throw new ReportTooManyRowsException(cap);
        }

        List<CostVsQuoteReport.Item> items = orders.stream().map(o -> item(o, rate)).toList();
        int pageSize = all ? Math.max(1, items.size()) : size;
        int start = (int) Math.min((long) page * pageSize, items.size());
        List<CostVsQuoteReport.Item> pageItems = items.subList(start, Math.min(start + pageSize, items.size()));
        int totalPages = all ? (items.isEmpty() ? 0 : 1) : (int) ((items.size() + (long) size - 1) / size);
        return new CostVsQuoteReport(period.from(), period.to(), period.zone().getId(), rate != null,
                rate != null ? CostVsQuoteReport.PARTS_AND_LABOUR : CostVsQuoteReport.PARTS_ONLY,
                summary(orders, items, rate), all ? 0 : page, pageSize, items.size(), totalPages, pageItems);
    }

    private static CostVsQuoteReport.Item item(DeliveredOrder o, @Nullable Long rate) {
        Long labour = rate == null ? null : Ratios.labourCostCents(o.trackedSeconds(), rate);
        long margin = o.netCents() - o.partsCostCents() - (labour == null ? 0 : labour);
        ApprovedQuote q = o.quote();
        CostVsQuoteReport.QuoteRef quote = q == null ? null
                : new CostVsQuoteReport.QuoteRef(q.id(), q.number(), q.approvedAt(), q.channel(), q.netCents(),
                        q.totalCents());
        Long difference = q == null ? null : o.totalCents() - q.totalCents();
        return new CostVsQuoteReport.Item(o.id(), o.number(), o.plate(), o.deliveredAt(), o.mechanicId(),
                o.mechanicName(), quote, o.netCents(), o.vatCents(), o.totalCents(), difference,
                q == null ? null : Ratios.percent(difference, q.totalCents()), scaled(o.standardHours()),
                Ratios.hours(o.trackedSeconds()), o.partsCostCents(), labour, margin,
                Ratios.percent(margin, o.netCents()));
    }

    private static CostVsQuoteReport.Summary summary(List<DeliveredOrder> orders, List<CostVsQuoteReport.Item> items,
            @Nullable Long rate) {
        long withQuote = 0;
        long quoteTotal = 0;
        long finalWithQuote = 0;
        long net = 0;
        long parts = 0;
        long labour = 0;
        long trackedSeconds = 0;
        BigDecimal standard = BigDecimal.ZERO;
        for (int i = 0; i < orders.size(); i++) {
            DeliveredOrder o = orders.get(i);
            if (o.quote() != null) {
                withQuote++;
                quoteTotal += o.quote().totalCents();
                finalWithQuote += o.totalCents();
            }
            net += o.netCents();
            parts += o.partsCostCents();
            trackedSeconds += o.trackedSeconds();
            standard = standard.add(o.standardHours());
            Long itemLabour = items.get(i).labourCostCents();
            labour += itemLabour == null ? 0 : itemLabour;
        }
        long difference = finalWithQuote - quoteTotal;
        long margin = net - parts - (rate == null ? 0 : labour);
        return new CostVsQuoteReport.Summary(orders.size(), withQuote, quoteTotal, finalWithQuote, difference,
                Ratios.percent(difference, quoteTotal), scaled(standard), Ratios.hours(trackedSeconds), net, parts,
                rate == null ? null : labour, margin, Ratios.percent(margin, net));
    }

    /// Productivity per mechanic and in total.
    public ProductivityReport productivity(UUID tenantId, @Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable UUID mechanicId) {
        ReportPeriod period = period(queries.workshop(tenantId), from, to);
        List<MechanicActivity> activity = queries.mechanicActivity(tenantId, period, mechanicId);
        long tracked = 0;
        long jobs = 0;
        long onJobs = 0;
        long workshop = 0;
        BigDecimal standard = BigDecimal.ZERO;
        for (MechanicActivity a : activity) {
            tracked += a.trackedSeconds();
            jobs += a.jobs();
            onJobs += a.trackedSecondsOnJobs();
            workshop += a.workshopSeconds();
            standard = standard.add(a.standardHours());
        }
        List<ProductivityReport.MechanicRow> rows = activity.stream()
                .map(a -> new ProductivityReport.MechanicRow(a.id(), a.name(), a.active(),
                        Ratios.hours(a.trackedSeconds()), a.jobs(), scaled(a.standardHours()),
                        Ratios.hours(a.trackedSecondsOnJobs()),
                        Ratios.efficiency(a.standardHours(), a.trackedSecondsOnJobs()),
                        Ratios.hoursPer(a.workshopSeconds(), a.jobs())))
                .toList();
        ProductivityReport.Totals totals = new ProductivityReport.Totals(Ratios.hours(tracked), jobs, scaled(standard),
                Ratios.hours(onJobs), Ratios.efficiency(standard, onJobs), Ratios.hoursPer(workshop, jobs));
        return new ProductivityReport(period.from(), period.to(), period.zone().getId(), rows, totals);
    }

    /// The KPIs of the period.
    public KpiReport kpis(UUID tenantId, @Nullable LocalDate from, @Nullable LocalDate to) {
        WorkshopContext workshop = queries.workshop(tenantId);
        ReportPeriod period = period(workshop, from, to);
        Long rate = workshop.labourCostPerHourCents();
        List<DeliveredOrder> orders = queries.deliveredOrders(tenantId, period, null, null, KPI_MAX_ORDERS + 1);
        if (orders.size() > KPI_MAX_ORDERS) {
            throw new ReportTooManyRowsException(KPI_MAX_ORDERS);
        }
        List<InvoicedOrder> invoices = queries.issuedInvoices(period);
        ReportQueries.RetentionCounts retention = queries.retention(period, workshop.retentionMonths());
        long comebacks = queries.comebacks(period, COMEBACK_DAYS);

        return new KpiReport(period.from(), period.to(), period.zone().getId(), rate != null,
                workshop.retentionMonths(), orders.size(), averageTime(orders, period), averageValue(orders, invoices),
                margin(orders, period, rate), retention(retention, period, workshop.retentionMonths()),
                new KpiReport.Comebacks(true, COMEBACK_DAYS, comebacks, Ratios.percent(comebacks, orders.size())),
                KpiReport.Unavailable.with("plan-12-bays"), KpiReport.Unavailable.with("satisfaction"),
                KpiReport.Unavailable.with("complaints"));
    }

    /// The retention rate and whether its follow-up window is over: it is final when `months` months after the last
    /// day of the period are not after today (in the workshop's zone).
    private KpiReport.Retention retention(ReportQueries.RetentionCounts counts, ReportPeriod period, int months) {
        LocalDate today = LocalDate.now(clock.withZone(period.zone()));
        boolean complete = !period.to().plusMonths(months).isAfter(today);
        int covered = complete ? months
                : (int) Math.max(0, Math.min(months, ChronoUnit.MONTHS.between(period.to(), today)));
        return new KpiReport.Retention(true, months, counts.customers(), counts.retained(),
                Ratios.percent(counts.retained(), counts.customers()), complete, covered);
    }

    private ReportPeriod period(WorkshopContext workshop, @Nullable LocalDate from, @Nullable LocalDate to) {
        ZoneId zone = workshop.zone();
        return ReportPeriod.of(from, to, zone, LocalDate.now(clock.withZone(zone)));
    }

    private KpiReport.AverageTimePerJob averageTime(List<DeliveredOrder> orders, ReportPeriod period) {
        long seconds = orders.stream().mapToLong(DeliveredOrder::trackedSeconds).sum();
        long withTime = orders.stream().filter(o -> o.trackedSeconds() > 0).count();
        List<KpiReport.ServiceTime> byService = queries.timeByService(period).stream()
                .sorted(Comparator.comparingLong(ServiceTime::seconds).reversed()
                        .thenComparing(ServiceTime::name).thenComparing(ServiceTime::serviceItemId))
                .limit(MAX_SERVICES)
                .map(s -> new KpiReport.ServiceTime(s.serviceItemId(), s.name(), Ratios.hours(s.seconds()),
                        s.orders(), Ratios.hoursPer(s.seconds(), s.orders())))
                .toList();
        return new KpiReport.AverageTimePerJob(true, Ratios.hours(seconds), withTime,
                Ratios.hoursPer(seconds, withTime), byService);
    }

    private KpiReport.AverageOrderValue averageValue(List<DeliveredOrder> orders, List<InvoicedOrder> invoices) {
        Set<UUID> invoiced = new HashSet<>();
        long count = 0;
        long credited = 0;
        long net = 0;
        long total = 0;
        for (InvoicedOrder i : invoices) {
            invoiced.add(i.workOrderId());
            Long creditTotal = i.creditTotalCents();
            if (creditTotal != null && creditTotal >= i.totalCents()) {
                credited++;
                continue;
            }
            count++;
            net += i.netCents() - (i.creditNetCents() == null ? 0 : i.creditNetCents());
            total += i.totalCents() - (creditTotal == null ? 0 : creditTotal);
        }
        long openOrders = 0;
        long openNet = 0;
        long openTotal = 0;
        for (DeliveredOrder o : orders) {
            if (!invoiced.contains(o.id())) {
                openOrders++;
                openNet += o.netCents();
                openTotal += o.totalCents();
            }
        }
        return new KpiReport.AverageOrderValue(true, KpiReport.SOURCE, count, credited, net, total,
                Ratios.averageCents(net, count), Ratios.averageCents(total, count),
                new KpiReport.NotYetInvoiced(openOrders, openNet, openTotal));
    }

    private KpiReport.Margin margin(List<DeliveredOrder> orders, ReportPeriod period, @Nullable Long rate) {
        long net = 0;
        long parts = 0;
        long labour = 0;
        for (DeliveredOrder o : orders) {
            net += o.netCents();
            parts += o.partsCostCents();
            labour += rate == null ? 0 : Ratios.labourCostCents(o.trackedSeconds(), rate);
        }
        long margin = net - parts - labour;
        PartLines partLines = queries.partLines(period);
        long packageMargin = partLines.packageNetCents() - partLines.packageCostCents();
        long otherNet = partLines.partNetCents() - partLines.packageNetCents();
        long otherCost = parts - partLines.packageCostCents();
        long otherMargin = otherNet - otherCost;
        List<KpiReport.ServiceMargin> byService = queries.serviceLines(period).stream().map(s -> {
            Long labourCost = rate == null ? null : Ratios.labourCostCents(s.trackedSeconds(), rate);
            long serviceMargin = s.netCents() - (labourCost == null ? 0 : labourCost);
            return new KpiReport.ServiceMargin(s.serviceItemId(), s.name(), s.lines(), s.orders(), s.netCents(),
                    Ratios.hours(s.trackedSeconds()), labourCost, serviceMargin,
                    Ratios.percent(serviceMargin, s.netCents()));
        }).sorted(Comparator.comparingLong(KpiReport.ServiceMargin::marginCents).reversed()
                .thenComparing(KpiReport.ServiceMargin::name).thenComparing(KpiReport.ServiceMargin::serviceItemId))
                .limit(MAX_SERVICES).toList();
        return new KpiReport.Margin(true, rate != null ? CostVsQuoteReport.PARTS_AND_LABOUR
                : CostVsQuoteReport.PARTS_ONLY, rate != null, orders.size(), net, parts, rate == null ? null : labour,
                margin, Ratios.percent(margin, net),
                new KpiReport.PackageParts(partLines.packageLines(), partLines.packageNetCents(),
                        partLines.packageCostCents(), packageMargin,
                        Ratios.percent(packageMargin, partLines.packageNetCents())),
                new KpiReport.OtherParts(otherNet, otherCost, otherMargin, Ratios.percent(otherMargin, otherNet)),
                byService);
    }

    private static BigDecimal scaled(BigDecimal hours) {
        return hours.setScale(2, RoundingMode.HALF_UP);
    }
}
