package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.asp.platform.workshop.domain.QuoteStatus;

/// Row-level security limits every query to the current workshop.
interface SpringDataQuoteRepository extends JpaRepository<QuoteEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<QuoteEntity> findById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<QuoteEntity> findByStatusTokenHash(String statusTokenHash);

    @Query("select q.workOrderId from QuoteEntity q where q.statusTokenHash = :tokenHash")
    Optional<UUID> findWorkOrderIdByTokenHash(String tokenHash);

    @Query("select q.workOrderId from QuoteEntity q where q.id = :id")
    Optional<UUID> findWorkOrderId(UUID id);

    @EntityGraph(attributePaths = "lines")
    List<QuoteEntity> findByWorkOrderIdOrderByNumberDesc(UUID workOrderId);

    @EntityGraph(attributePaths = "lines")
    Optional<QuoteEntity> findByWorkOrderIdAndStatus(UUID workOrderId, QuoteStatus status);

    /// The newest quote of each order (numbers grow with time in a workshop); the lines stay unloaded.
    @Query("""
            select q from QuoteEntity q
            where q.workOrderId in :workOrderIds
              and q.number = (select max(q2.number) from QuoteEntity q2 where q2.workOrderId = q.workOrderId)""")
    List<QuoteEntity> findLatestOf(Collection<UUID> workOrderIds);

    /// `status = 'SENT'` is a literal so the planner uses the partial index `quote_tenant_sent_valid_idx` (V18).
    @Query(nativeQuery = true, value = """
            select id from quote
            where status = 'SENT' and valid_until < :now
            order by valid_until, number
            limit :limit""")
    List<UUID> findDueIds(Instant now, int limit);

    /// Row-level security limits the max to the current workshop.
    @Query("select coalesce(max(q.number), 0) + 1 from QuoteEntity q")
    int nextNumber();
}
