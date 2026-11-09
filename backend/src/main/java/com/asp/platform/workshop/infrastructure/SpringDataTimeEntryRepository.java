package com.asp.platform.workshop.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/// Row-level security limits every query to the current workshop.
interface SpringDataTimeEntryRepository extends JpaRepository<TimeEntryEntity, UUID> {

    List<TimeEntryEntity> findByWorkOrderIdOrderByStartedAtDescIdDesc(UUID workOrderId);

    Optional<TimeEntryEntity> findByMechanicIdAndStoppedAtIsNull(UUID mechanicId);

    List<TimeEntryEntity> findByWorkOrderIdAndStoppedAtIsNull(UUID workOrderId);
}
