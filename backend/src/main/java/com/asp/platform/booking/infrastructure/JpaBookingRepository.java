package com.asp.platform.booking.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.asp.platform.booking.domain.Booking;
import com.asp.platform.booking.domain.BookingRepository;
import com.asp.platform.booking.domain.BookingStatus;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaBookingRepository implements BookingRepository {

    private static final Set<BookingStatus> HOLDING_A_PLACE = Set.of(BookingStatus.REQUESTED,
            BookingStatus.CONFIRMED);

    private final SpringDataBookingRepository jpa;

    JpaBookingRepository(SpringDataBookingRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Booking save(Booking booking) {
        return Upserts.upsert(jpa, booking.id(), booking, BookingEntity::new);
    }

    @Override
    public Optional<Booking> findById(UUID id) {
        return jpa.findById(id).map(BookingEntity::toDomain);
    }

    @Override
    public Optional<Booking> findByTokenHash(String tokenHash) {
        return jpa.findByStatusTokenHash(tokenHash).map(BookingEntity::toDomain);
    }

    @Override
    public List<Booking> findBetween(Instant from, Instant to, @Nullable BookingStatus status) {
        List<BookingEntity> found = status == null ? jpa.findBetween(from, to) : jpa.findBetween(from, to, status);
        return found.stream().map(BookingEntity::toDomain).toList();
    }

    @Override
    public Map<Instant, Integer> placesTaken(Instant from, Instant to) {
        Map<Instant, Integer> taken = new HashMap<>();
        for (Object[] row : jpa.countBySlot(HOLDING_A_PLACE, from, to)) {
            taken.put((Instant) row[0], ((Number) row[1]).intValue());
        }
        return Map.copyOf(taken);
    }

    @Override
    public int placesTakenAt(Instant slotStart) {
        return Math.toIntExact(jpa.countAt(HOLDING_A_PLACE, slotStart));
    }

    @Override
    public long countRequested() {
        return jpa.countByStatus(BookingStatus.REQUESTED);
    }

    @Override
    public int nextNumber() {
        return jpa.nextNumber();
    }
}
