package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

/// Row-level security limits every query to the current workshop.
interface SpringDataInvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<InvoiceEntity> findById(UUID id);

    /// Waits 3 s at most for the row, then fails with a pessimistic locking error (409 try-again). No fetch join:
    /// PostgreSQL cannot lock the nullable side of an outer join; the lines load lazily.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select i from InvoiceEntity i where i.id = :id")
    Optional<InvoiceEntity> lockById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<InvoiceEntity> findByRequestId(UUID requestId);

    /// Literal statuses so the planner can use the partial unique index of V20.
    @EntityGraph(attributePaths = "lines")
    @Query("""
            select i from InvoiceEntity i
            where i.workOrderId = :workOrderId and i.kind = com.asp.platform.billing.domain.DocumentKind.INVOICE
              and i.status in (com.asp.platform.billing.domain.InvoiceStatus.PENDING,
                               com.asp.platform.billing.domain.InvoiceStatus.ISSUED,
                               com.asp.platform.billing.domain.InvoiceStatus.FAILED)""")
    Optional<InvoiceEntity> findActiveInvoice(UUID workOrderId);

    @EntityGraph(attributePaths = "lines")
    @Query("""
            select i from InvoiceEntity i
            where i.creditOfId = :invoiceId and i.kind = com.asp.platform.billing.domain.DocumentKind.CREDIT_NOTE
              and i.status in (com.asp.platform.billing.domain.InvoiceStatus.PENDING,
                               com.asp.platform.billing.domain.InvoiceStatus.ISSUED,
                               com.asp.platform.billing.domain.InvoiceStatus.FAILED)""")
    Optional<InvoiceEntity> findActiveCreditNote(UUID invoiceId);

    @EntityGraph(attributePaths = "lines")
    List<InvoiceEntity> findByWorkOrderIdOrderByCreatedAtDescIdDesc(UUID workOrderId);

    @EntityGraph(attributePaths = "lines")
    @Query("""
            select i from InvoiceEntity i
            where i.creditOfId in :invoiceIds and i.kind = com.asp.platform.billing.domain.DocumentKind.CREDIT_NOTE
              and i.status = com.asp.platform.billing.domain.InvoiceStatus.ISSUED""")
    List<InvoiceEntity> findIssuedCreditNotes(Collection<UUID> invoiceIds);

    /// The retry job; `status = 'PENDING'` is a literal so the planner uses the partial index `invoice_tenant_pending_idx`.
    @Query(nativeQuery = true, value = """
            select id from invoice
            where status = 'PENDING' and attempts < :maxAttempts
              and coalesce(attempt_started_at, last_attempt_at, created_at) <= :before
            order by coalesce(attempt_started_at, last_attempt_at, created_at)
            limit :limit""")
    List<UUID> findRetryable(Instant before, int maxAttempts, int limit);
}
