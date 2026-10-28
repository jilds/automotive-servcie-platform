package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Constraints;
import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteHeadline;
import com.asp.platform.workshop.domain.QuoteNumberTakenException;
import com.asp.platform.workshop.domain.QuoteRepository;
import com.asp.platform.workshop.domain.QuoteStatus;

@Repository
class JpaQuoteRepository implements QuoteRepository {

    /// The `(tenant_id, number)` key of V18.
    static final String UNIQUE_NUMBER_CONSTRAINT = "quote_tenant_number_key";

    private final SpringDataQuoteRepository jpa;

    JpaQuoteRepository(SpringDataQuoteRepository jpa) {
        this.jpa = jpa;
    }

    /// Two work orders sent at the same moment read the same next number; the second insert breaks the unique key
    /// and answers 409 `try-again` (the work order lock serialises the sends of one order only).
    @Override
    public Quote save(Quote quote) {
        try {
            return Upserts.upsert(jpa, quote.id(), quote, QuoteEntity::new);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.violated(e, UNIQUE_NUMBER_CONSTRAINT)) {
                throw new QuoteNumberTakenException();
            }
            throw e;
        }
    }

    @Override
    public Optional<Quote> findById(UUID id) {
        return jpa.findById(id).map(QuoteEntity::toDomain);
    }

    @Override
    public Optional<Quote> findByTokenHash(String tokenHash) {
        return jpa.findByStatusTokenHash(tokenHash).map(QuoteEntity::toDomain);
    }

    @Override
    public Optional<UUID> findWorkOrderIdByTokenHash(String tokenHash) {
        return jpa.findWorkOrderIdByTokenHash(tokenHash);
    }

    @Override
    public Optional<UUID> findWorkOrderId(UUID quoteId) {
        return jpa.findWorkOrderId(quoteId);
    }

    @Override
    public List<Quote> findByWorkOrder(UUID workOrderId) {
        return jpa.findByWorkOrderIdOrderByNumberDesc(workOrderId).stream().map(QuoteEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Quote> findSent(UUID workOrderId) {
        return jpa.findByWorkOrderIdAndStatus(workOrderId, QuoteStatus.SENT).map(QuoteEntity::toDomain);
    }

    @Override
    public Map<UUID, QuoteHeadline> latestOf(Collection<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findLatestOf(workOrderIds).stream().map(QuoteEntity::headline)
                .collect(Collectors.toUnmodifiableMap(QuoteHeadline::workOrderId, h -> h));
    }

    @Override
    public List<UUID> findDueIds(Instant now, int limit) {
        return jpa.findDueIds(now, limit);
    }

    @Override
    public int nextNumber() {
        return jpa.nextNumber();
    }
}
