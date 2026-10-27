package com.asp.platform.stock.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

interface SpringDataProductRepository extends JpaRepository<ProductEntity, UUID> {

    @Query("""
            select p from ProductEntity p
            where (:q is null
                   or lower(p.sku) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.brand) like lower(concat('%', cast(:q as string), '%')))
              and (p.active = true or :activeOnly = false)
            order by lower(p.name), p.id""")
    List<ProductEntity> search(@Nullable String q, boolean activeOnly);

    /// Rows of (product, reserved quantity): active products listed online, with the sum of their active
    /// reservations from a correlated subquery, so the shop list needs one query.
    @Query("""
            select p, coalesce((select sum(r.quantity) from StockReservationEntity r
                                where r.productId = p.id
                                  and r.status = com.asp.platform.stock.domain.ReservationStatus.ACTIVE), 0)
            from ProductEntity p
            where p.active = true and p.listedOnline = true
              and (:q is null
                   or lower(p.sku) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.brand) like lower(concat('%', cast(:q as string), '%')))
            order by lower(p.name), p.id""")
    List<Object[]> onlineStock(@Nullable String q, Limit limit);

    /// Same lock timeout as the team lock in identity: a request waits 3 s at most, then answers 409 `try-again`.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from ProductEntity p where p.id = :id")
    Optional<ProductEntity> lockById(UUID id);

    @Query("select count(p) > 0 from ProductEntity p where lower(p.sku) = lower(:sku)")
    boolean skuTaken(String sku);

    @Query("select count(p) > 0 from ProductEntity p where lower(p.sku) = lower(:sku) and p.id <> :exceptId")
    boolean skuTakenByOther(String sku, UUID exceptId);
}
