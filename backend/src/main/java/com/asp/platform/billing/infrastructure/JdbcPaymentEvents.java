package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.billing.domain.PaymentEventRepository;

/// Plain SQL on `payment_event`. Row-level security adds the workshop filter and `tenant_id` comes from the connection.
@Repository
class JdbcPaymentEvents implements PaymentEventRepository {

    private final JdbcClient jdbc;

    JdbcPaymentEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /// A transaction-scoped advisory lock on the workshop, provider and event id: two copies of one event run one after
    /// the other, so the second sees the first one's row.
    @Override
    public void lock(String provider, String eventId) {
        jdbc.sql("""
                select pg_advisory_xact_lock(hashtextextended(
                    coalesce(current_setting('app.tenant_id', true), '') || ':' || :provider || ':' || :event, 0))""")
                .param("provider", provider).param("event", eventId)
                .query((rs, _) -> 1).list();
    }

    @Override
    public boolean exists(String provider, String eventId) {
        return jdbc.sql("select exists (select 1 from payment_event where provider = :provider and event_id = :event)")
                .param("provider", provider).param("event", eventId)
                .query(Boolean.class).single();
    }

    @Override
    public void record(String provider, String eventId, @Nullable UUID paymentId, boolean applied,
            @Nullable String detail, Instant now) {
        jdbc.sql("""
                insert into payment_event (id, provider, event_id, payment_id, outcome, detail, received_at)
                values (:id, :provider, :event, :payment, :outcome, :detail, :at)""")
                .param("id", UUID.randomUUID()).param("provider", provider).param("event", eventId)
                .param("payment", paymentId, java.sql.Types.OTHER)
                .param("outcome", applied ? "APPLIED" : "IGNORED")
                .param("detail", detail, java.sql.Types.VARCHAR)
                .param("at", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }
}
