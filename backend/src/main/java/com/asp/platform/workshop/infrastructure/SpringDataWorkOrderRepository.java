package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.asp.platform.workshop.domain.WorkOrderStatus;

interface SpringDataWorkOrderRepository extends JpaRepository<WorkOrderEntity, UUID> {

    /// Row-level security scopes this to the current workshop. The unique (tenant_id, number) constraint
    /// catches the rare race of two simultaneous check-ins.
    @Query("select coalesce(max(w.number), 0) + 1 from WorkOrderEntity w")
    int nextNumber();

    /// Same lock timeout as `SpringDataProductRepository.lockById`: a request waits 3 s at most, then answers 409
    /// `try-again`. No fetch join: PostgreSQL cannot lock the nullable side of an outer join; the lines load lazily.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select w from WorkOrderEntity w where w.id = :id")
    Optional<WorkOrderEntity> lockById(UUID id);

    @EntityGraph(attributePaths = "lines")
    List<WorkOrderEntity> findByStatusInOrderByStatusChangedAt(Collection<WorkOrderStatus> statuses);

    @EntityGraph(attributePaths = "lines")
    List<WorkOrderEntity> findByAssignedMechanicIdAndStatusInOrderByStatusChangedAt(UUID mechanicId,
            Collection<WorkOrderStatus> statuses);

    @EntityGraph(attributePaths = "lines")
    List<WorkOrderEntity> findByVehicleIdOrderByNumberDesc(UUID vehicleId);

    /// Open orders plus recently delivered ones that nobody hid yet.
    @EntityGraph(attributePaths = "lines")
    @Query("""
            select w from WorkOrderEntity w
            where w.status in :open
               or (w.status = com.asp.platform.workshop.domain.WorkOrderStatus.DELIVERED
                   and w.boardHiddenAt is null and w.statusChangedAt >= :since)
            order by w.statusChangedAt""")
    List<WorkOrderEntity> findBoard(@Param("open") Collection<WorkOrderStatus> open, @Param("since") Instant since);

    @EntityGraph(attributePaths = "lines")
    @Query("""
            select w from WorkOrderEntity w
            where w.assignedMechanicId = :mechanicId
              and (w.status in :open
                   or (w.status = com.asp.platform.workshop.domain.WorkOrderStatus.DELIVERED
                       and w.boardHiddenAt is null and w.statusChangedAt >= :since))
            order by w.statusChangedAt""")
    List<WorkOrderEntity> findBoardOf(@Param("mechanicId") UUID mechanicId,
            @Param("open") Collection<WorkOrderStatus> open, @Param("since") Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update WorkOrderEntity w set w.boardHiddenAt = :now, w.boardHiddenBy = :by
            where w.id = :id and w.status = com.asp.platform.workshop.domain.WorkOrderStatus.DELIVERED""")
    int hide(@Param("id") UUID id, @Param("by") UUID by, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update WorkOrderEntity w set w.boardHiddenAt = :now, w.boardHiddenBy = :by
            where w.status = com.asp.platform.workshop.domain.WorkOrderStatus.DELIVERED and w.boardHiddenAt is null""")
    int hideAllDelivered(@Param("by") UUID by, @Param("now") Instant now);
}
