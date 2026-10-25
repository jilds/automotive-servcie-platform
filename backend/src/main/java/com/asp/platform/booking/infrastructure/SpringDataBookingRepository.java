package com.asp.platform.booking.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.asp.platform.booking.domain.BookingStatus;

/// Row-level security limits every query to the current workshop.
interface SpringDataBookingRepository extends JpaRepository<BookingEntity, UUID> {

    @EntityGraph(attributePaths = "services")
    Optional<BookingEntity> findByStatusTokenHash(String statusTokenHash);

    @EntityGraph(attributePaths = "services")
    @Query("""
            select b from BookingEntity b
            where b.slotStart >= :from and b.slotStart < :to
            order by b.slotStart, b.number""")
    List<BookingEntity> findBetween(Instant from, Instant to);

    @EntityGraph(attributePaths = "services")
    @Query("""
            select b from BookingEntity b
            where b.slotStart >= :from and b.slotStart < :to and b.status = :status
            order by b.slotStart, b.number""")
    List<BookingEntity> findBetween(Instant from, Instant to, BookingStatus status);

    /// Rows of (slot start, count).
    @Query("""
            select b.slotStart, count(b) from BookingEntity b
            where b.status in :statuses and b.slotStart >= :from and b.slotStart < :to
            group by b.slotStart""")
    List<Object[]> countBySlot(Collection<BookingStatus> statuses, Instant from, Instant to);

    @Query("select count(b) from BookingEntity b where b.status in :statuses and b.slotStart = :slotStart")
    long countAt(Collection<BookingStatus> statuses, Instant slotStart);

    long countByStatus(BookingStatus status);

    /// Row-level security limits the max to the current workshop.
    @Query("select coalesce(max(b.number), 0) + 1 from BookingEntity b")
    int nextNumber();
}
