package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.PaymentRepository;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataPaymentRepository jpa;
    private final JdbcClient jdbc;

    JpaPaymentRepository(SpringDataPaymentRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public Payment save(Payment payment) {
        return Upserts.upsert(jpa, payment.id(), payment, PaymentEntity::new);
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpa.findById(id).map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> lockById(UUID id) {
        return jpa.lockById(id).map(PaymentEntity::toDomain);
    }

    @Override
    public List<Payment> lockByInvoice(UUID invoiceId) {
        return jpa.lockByInvoice(invoiceId).stream().map(PaymentEntity::toDomain).toList();
    }

    @Override
    public Optional<Ref> refOf(UUID paymentId) {
        return jdbc.sql("select id, invoice_id from payment where id = :id").param("id", paymentId)
                .query((rs, _) -> new Ref(rs.getObject("id", UUID.class), rs.getObject("invoice_id", UUID.class)))
                .optional();
    }

    @Override
    public Optional<Ref> refOfProviderPayment(String provider, String providerPaymentId) {
        return jdbc.sql("select id, invoice_id from payment where provider = :provider "
                + "and provider_payment_id = :ppid").param("provider", provider).param("ppid", providerPaymentId)
                .query((rs, _) -> new Ref(rs.getObject("id", UUID.class), rs.getObject("invoice_id", UUID.class)))
                .optional();
    }

    @Override
    public Optional<Payment> findByProviderPaymentId(String provider, String providerPaymentId) {
        return jpa.findByProviderAndProviderPaymentId(provider, providerPaymentId).map(PaymentEntity::toDomain);
    }

    @Override
    public List<Payment> findByInvoice(UUID invoiceId) {
        return jpa.findByInvoiceIdOrderByCreatedAtAscIdAsc(invoiceId).stream().map(PaymentEntity::toDomain).toList();
    }

    @Override
    public List<Payment> findByInvoices(Collection<UUID> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return List.of();
        }
        return jpa.findByInvoiceIdInOrderByCreatedAtAscIdAsc(invoiceIds).stream().map(PaymentEntity::toDomain)
                .toList();
    }

    /// Keyset paging on (expires_at, id): `status = 'PENDING'` is a literal so the planner uses the partial index
    /// `payment_tenant_pending_expiry_idx`, and a payment that stays pending never makes the next page start over.
    @Override
    public List<Cursor> findDueForExpiry(Instant now, @Nullable Cursor after, int limit) {
        String sql = "select expires_at, id from payment where status = 'PENDING' and expires_at < :now"
                + (after == null ? "" : " and (expires_at, id) > (:afterAt, :afterId)")
                + " order by expires_at, id limit :limit";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("limit", limit);
        if (after != null) {
            spec = spec.param("afterAt", OffsetDateTime.ofInstant(after.expiresAt(), ZoneOffset.UTC))
                    .param("afterId", after.id());
        }
        return spec.query((rs, _) -> new Cursor(rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                rs.getObject("id", UUID.class))).list();
    }
}
