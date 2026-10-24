package com.asp.platform.stock.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataStockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {

    List<StockMovementEntity> findByProductIdOrderByChangedAtDescIdDesc(UUID productId, Limit limit);
}
