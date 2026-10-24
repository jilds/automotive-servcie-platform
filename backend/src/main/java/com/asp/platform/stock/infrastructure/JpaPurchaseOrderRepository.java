package com.asp.platform.stock.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.stock.domain.PurchaseOrder;
import com.asp.platform.stock.domain.PurchaseOrderRepository;
import com.asp.platform.stock.domain.PurchaseOrderStatus;

@Repository
class JpaPurchaseOrderRepository implements PurchaseOrderRepository {

    private final SpringDataPurchaseOrderRepository jpa;

    JpaPurchaseOrderRepository(SpringDataPurchaseOrderRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PurchaseOrder save(PurchaseOrder order) {
        return Upserts.upsert(jpa, order.id(), order, PurchaseOrderEntity::new);
    }

    @Override
    public Optional<PurchaseOrder> findById(UUID id) {
        return jpa.findById(id).map(PurchaseOrderEntity::toDomain);
    }

    @Override
    public List<PurchaseOrder> list(@Nullable PurchaseOrderStatus status) {
        List<PurchaseOrderEntity> found = status == null
                ? jpa.findAllByOrderByNumberDesc()
                : jpa.findByStatusOrderByNumberDesc(status);
        return found.stream().map(PurchaseOrderEntity::toDomain).toList();
    }

    @Override
    public int nextNumber() {
        return jpa.nextNumber();
    }
}
