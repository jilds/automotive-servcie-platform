package com.asp.platform.stock.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.asp.platform.stock.ReservationSource;

/// Row-level security limits every query to the current workshop.
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    /// What the active reservations of a product hold together; zero when there are none.
    BigDecimal reservedQuantity(UUID productId);

    /// [#reservedQuantity] for several products in one query; products without active reservations are left out.
    Map<UUID, BigDecimal> reservedQuantities(Collection<UUID> productIds);

    Optional<Reservation> findActiveByLine(ReservationSource source, UUID sourceLineId);

    List<Reservation> findActiveBySource(ReservationSource source, UUID sourceId);
}
