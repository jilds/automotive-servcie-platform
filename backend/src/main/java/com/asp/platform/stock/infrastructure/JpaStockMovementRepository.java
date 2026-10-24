package com.asp.platform.stock.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import com.asp.platform.stock.domain.StockMovement;
import com.asp.platform.stock.domain.StockMovementRepository;

@Repository
class JpaStockMovementRepository implements StockMovementRepository {

    private final SpringDataStockMovementRepository jpa;

    JpaStockMovementRepository(SpringDataStockMovementRepository jpa) {
        this.jpa = jpa;
    }

    /// Not [com.asp.platform.shared.persistence.Upserts]: the ledger only adds rows, so this is a plain insert.
    @Override
    public void add(StockMovement movement) {
        jpa.save(new StockMovementEntity(movement));
    }

    @Override
    public List<StockMovement> findByProduct(UUID productId, int limit) {
        return jpa.findByProductIdOrderByChangedAtDescIdDesc(productId, Limit.of(limit)).stream()
                .map(StockMovementEntity::toDomain).toList();
    }
}
