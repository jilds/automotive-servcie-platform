package com.asp.platform.stock.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.domain.ReservationStatus;

interface SpringDataStockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {

    /// Null when the product has no active reservation.
    @Query("""
            select sum(r.quantity) from StockReservationEntity r
            where r.productId = :productId and r.status = com.asp.platform.stock.domain.ReservationStatus.ACTIVE""")
    @Nullable BigDecimal reservedQuantity(UUID productId);

    @Query("""
            select r.productId as productId, sum(r.quantity) as quantity from StockReservationEntity r
            where r.productId in :productIds and r.status = com.asp.platform.stock.domain.ReservationStatus.ACTIVE
            group by r.productId""")
    List<ProductQuantity> reservedQuantities(Collection<UUID> productIds);

    Optional<StockReservationEntity> findBySourceAndSourceLineIdAndStatus(ReservationSource source,
            UUID sourceLineId, ReservationStatus status);

    List<StockReservationEntity> findBySourceAndSourceIdAndStatusOrderByProductId(ReservationSource source,
            UUID sourceId, ReservationStatus status);

    interface ProductQuantity {
        UUID getProductId();

        BigDecimal getQuantity();
    }
}
