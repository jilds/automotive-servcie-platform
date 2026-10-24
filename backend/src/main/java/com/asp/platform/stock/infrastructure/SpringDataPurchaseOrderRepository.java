package com.asp.platform.stock.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.asp.platform.stock.domain.PurchaseOrderStatus;

interface SpringDataPurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, UUID> {

    /// Row-level security limits the max to the current workshop; the unique (tenant_id, number) constraint
    /// catches the rare race of two orders created at the same moment.
    @Query("select coalesce(max(p.number), 0) + 1 from PurchaseOrderEntity p")
    int nextNumber();

    @EntityGraph(attributePaths = "lines")
    List<PurchaseOrderEntity> findAllByOrderByNumberDesc();

    @EntityGraph(attributePaths = "lines")
    List<PurchaseOrderEntity> findByStatusOrderByNumberDesc(PurchaseOrderStatus status);
}
