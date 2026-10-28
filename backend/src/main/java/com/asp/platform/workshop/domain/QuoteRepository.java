package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface QuoteRepository {

    /// Flushes, so the returned quote carries the stored version and a second SENT quote or a taken number fails
    /// here.
    ///
    /// @throws QuoteNumberTakenException when another quote of the workshop took the number meanwhile
    Quote save(Quote quote);

    Optional<Quote> findById(UUID id);

    /// @param tokenHash [com.asp.platform.shared.StatusTokens#hash] of the link's token
    Optional<Quote> findByTokenHash(String tokenHash);

    /// The work order of the quote with this token, without loading the quote, so the caller can lock the order
    /// before it reads the quote.
    Optional<UUID> findWorkOrderIdByTokenHash(String tokenHash);

    /// The work order of a quote, without loading the quote (see [#findWorkOrderIdByTokenHash]).
    Optional<UUID> findWorkOrderId(UUID quoteId);

    /// The quotes of a work order with their lines, newest first.
    List<Quote> findByWorkOrder(UUID workOrderId);

    /// The SENT quote of a work order, if any (at most one).
    Optional<Quote> findSent(UUID workOrderId);

    /// The latest quote of each of these work orders, in one query, keyed by work order; orders without quotes are
    /// left out.
    Map<UUID, QuoteHeadline> latestOf(Collection<UUID> workOrderIds);

    /// Ids of SENT quotes whose validity ended before `now`, earliest first, at most `limit`.
    List<UUID> findDueIds(Instant now, int limit);

    /// The next quote number of the workshop; the unique (tenant, number) constraint catches a race.
    int nextNumber();
}
