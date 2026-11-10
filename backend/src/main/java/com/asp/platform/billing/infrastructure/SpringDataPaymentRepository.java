package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

/// Row-level security limits every query to the current workshop.
interface SpringDataPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> lockById(UUID id);

    Optional<PaymentEntity> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    List<PaymentEntity> findByInvoiceIdOrderByCreatedAtAscIdAsc(UUID invoiceId);

    List<PaymentEntity> findByInvoiceIdInOrderByCreatedAtAscIdAsc(Collection<UUID> invoiceIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from PaymentEntity p where p.invoiceId = :invoiceId order by p.id")
    List<PaymentEntity> lockByInvoice(UUID invoiceId);
}
