package com.asp.platform.stock.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.domain.Reservation;
import com.asp.platform.stock.domain.ReservationRepository;
import com.asp.platform.stock.domain.ReservationStatus;

@Repository
class JpaReservationRepository implements ReservationRepository {

    private final SpringDataStockReservationRepository jpa;

    JpaReservationRepository(SpringDataStockReservationRepository jpa) {
        this.jpa = jpa;
    }

    /// Flushes at once: the next "how much is reserved?" query must see it.
    @Override
    public Reservation save(Reservation reservation) {
        return Upserts.upsert(jpa, reservation.id(), reservation, StockReservationEntity::new);
    }

    @Override
    public BigDecimal reservedQuantity(UUID productId) {
        BigDecimal sum = jpa.reservedQuantity(productId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public Map<UUID, BigDecimal> reservedQuantities(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return jpa.reservedQuantities(productIds).stream()
                .collect(Collectors.toMap(SpringDataStockReservationRepository.ProductQuantity::getProductId,
                        SpringDataStockReservationRepository.ProductQuantity::getQuantity));
    }

    @Override
    public Optional<Reservation> findActiveByLine(ReservationSource source, UUID sourceLineId) {
        return jpa.findBySourceAndSourceLineIdAndStatus(source, sourceLineId, ReservationStatus.ACTIVE)
                .map(StockReservationEntity::toDomain);
    }

    @Override
    public List<Reservation> findActiveBySource(ReservationSource source, UUID sourceId) {
        return jpa.findBySourceAndSourceIdAndStatusOrderByProductId(source, sourceId, ReservationStatus.ACTIVE)
                .stream().map(StockReservationEntity::toDomain).toList();
    }
}
