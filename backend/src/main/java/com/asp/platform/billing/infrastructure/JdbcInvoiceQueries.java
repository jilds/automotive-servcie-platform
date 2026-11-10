package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.InvoiceQueries;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.PaymentProgress;

/// The invoice list in plain SQL. Row-level security adds the workshop filter, so the queries never name a tenant.
/// The payment progress is computed here (paid sum and credit note) so it can be filtered and paged in the database.
@Repository
class JdbcInvoiceQueries implements InvoiceQueries {

    /// The same rules as the domain's Settlement: only an issued invoice has a progress; a credit note that was
    /// issued makes it CREDITED.
    private static final String WITH = """
            with a as (
                select i.id, i.kind, i.status, i.full_number, i.work_order_id, i.work_order_number, i.customer_name,
                       i.customer_nif, i.plate, i.issued_at, i.created_at, i.total_cents, i.provider,
                       coalesce((select sum(p.amount_cents) from payment p
                                 where p.invoice_id = i.id and p.status = 'PAID'), 0) as paid,
                       exists (select 1 from invoice c where c.credit_of_id = i.id and c.kind = 'CREDIT_NOTE'
                                                         and c.status = 'ISSUED') as credited
                from invoice i
            ), b as (
                select a.*,
                       case when a.kind <> 'INVOICE' or a.status <> 'ISSUED' then null
                            when a.credited then 'CREDITED'
                            when a.paid >= a.total_cents then 'PAID'
                            when a.paid > 0 then 'PARTIALLY_PAID'
                            else 'UNPAID' end as progress
                from a
            )""";

    private final JdbcClient jdbc;

    JdbcInvoiceQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page find(Filter filter, int page, int size) {
        List<String> where = new ArrayList<>();
        var params = new java.util.LinkedHashMap<String, Object>();
        if (!filter.statuses().isEmpty()) {
            where.add("status in (:statuses)");
            params.put("statuses", filter.statuses().stream().map(Enum::name).toList());
        }
        if (filter.kind() != null) {
            where.add("kind = :kind");
            params.put("kind", filter.kind().name());
        }
        if (filter.progress() != null) {
            where.add("progress = :progress");
            params.put("progress", filter.progress().name());
        }
        if (filter.from() != null) {
            where.add("created_at >= :from");
            params.put("from", OffsetDateTime.ofInstant(filter.from(), ZoneOffset.UTC));
        }
        if (filter.to() != null) {
            where.add("created_at < :to");
            params.put("to", OffsetDateTime.ofInstant(filter.to(), ZoneOffset.UTC));
        }
        if (filter.search() != null) {
            where.add("""
                    (coalesce(full_number, '') ilike :q escape '\\' or customer_name ilike :q escape '\\'
                     or coalesce(customer_nif, '') ilike :q escape '\\' or coalesce(plate, '') ilike :q escape '\\')""");
            params.put("q", "%" + escape(filter.search()) + "%");
        }
        String filtered = where.isEmpty() ? "" : " where " + String.join(" and ", where);

        long total = bind(jdbc.sql(WITH + " select count(*) from b" + filtered), params).query(Long.class).single();
        List<Row> rows = bind(jdbc.sql(WITH + """
                 select id, kind, status, full_number, work_order_id, work_order_number, customer_name, customer_nif,
                        plate, issued_at, created_at, total_cents, provider, progress,
                        case when progress is not null and progress <> 'CREDITED'
                             then greatest(total_cents - paid, 0) else 0 end as balance,
                        case when status = 'VOIDED' or credited then paid else 0 end as refund
                 from b""" + filtered + " order by created_at desc, id desc limit :size offset :offset"), params)
                .param("size", size).param("offset", (long) page * size)
                .query((rs, _) -> new Row(rs.getObject("id", UUID.class), DocumentKind.valueOf(rs.getString("kind")),
                        InvoiceStatus.valueOf(rs.getString("status")), rs.getString("full_number"),
                        rs.getObject("work_order_id", UUID.class), rs.getInt("work_order_number"),
                        rs.getString("customer_name"), rs.getString("customer_nif"), rs.getString("plate"),
                        instant(rs.getObject("issued_at", OffsetDateTime.class)),
                        java.util.Objects.requireNonNull(instant(rs.getObject("created_at", OffsetDateTime.class))),
                        rs.getLong("total_cents"), rs.getLong("balance"), rs.getLong("refund"),
                        rs.getString("progress") == null ? null : PaymentProgress.valueOf(rs.getString("progress")),
                        rs.getString("provider")))
                .list();
        return new Page(rows, page, size, total);
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, java.util.Map<String, Object> params) {
        JdbcClient.StatementSpec bound = spec;
        for (var entry : params.entrySet()) {
            bound = bound.param(entry.getKey(), entry.getValue());
        }
        return bound;
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /// Makes `%`, `_` and the escape character literal in a LIKE pattern.
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
