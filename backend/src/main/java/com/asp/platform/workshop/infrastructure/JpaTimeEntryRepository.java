package com.asp.platform.workshop.infrastructure;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Constraints;
import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.workshop.domain.TimeEntry;
import com.asp.platform.workshop.domain.TimeEntryOverlapException;
import com.asp.platform.workshop.domain.TimeEntryRepository;
import com.asp.platform.workshop.domain.TimerRaceException;

@Repository
class JpaTimeEntryRepository implements TimeEntryRepository {

    /// The partial unique index of V19: one running entry per mechanic.
    static final String ONE_RUNNING_INDEX = "work_time_entry_one_running_idx";

    private final SpringDataTimeEntryRepository jpa;
    private final JdbcClient jdbc;

    JpaTimeEntryRepository(SpringDataTimeEntryRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    /// No two entries of one mechanic overlap (adjacent ones, stop = next start, do not). The check runs under a
    /// transaction-scoped advisory lock on the mechanic, so two requests on different work orders cannot both pass it
    /// (the work order lock only serialises one order). Flushes, so a mechanic's stopped entry reaches the database
    /// before the next one starts (Hibernate would run the insert first and break the unique index).
    ///
    /// @throws TimeEntryOverlapException when the entry overlaps another of the same mechanic
    @Override
    public TimeEntry save(TimeEntry entry) {
        lockMechanic(entry.mechanicId());
        if (overlaps(entry)) {
            throw new TimeEntryOverlapException();
        }
        try {
            return Upserts.upsert(jpa, entry.id(), entry, TimeEntryEntity::new);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.violated(e, ONE_RUNNING_INDEX)) {
                throw new TimerRaceException();
            }
            throw e;
        }
    }

    @Override
    public void lockMechanic(UUID mechanicId) {
        jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) l")
                .param("key", "work_time_entry:" + mechanicId)
                .query(Integer.class).single();
    }

    private boolean overlaps(TimeEntry entry) {
        Instant stop = entry.stoppedAt();
        return jdbc.sql("""
                select exists (
                    select 1 from work_time_entry
                    where mechanic_id = :mechanic and id <> :id
                      and started_at < coalesce(cast(:stop as timestamptz), 'infinity')
                      and coalesce(stopped_at, 'infinity') > cast(:start as timestamptz)
                      and tstzrange(started_at, coalesce(stopped_at, 'infinity'), '[)')
                          && tstzrange(cast(:start as timestamptz), coalesce(cast(:stop as timestamptz), 'infinity'),
                                       '[)'))""")
                .param("mechanic", entry.mechanicId())
                .param("id", entry.id())
                .param("start", utc(entry.startedAt()))
                .param("stop", stop == null ? null : utc(stop), Types.TIMESTAMP_WITH_TIMEZONE)
                .query(Boolean.class).single();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @Override
    public Optional<TimeEntry> findById(UUID id) {
        return jpa.findById(id).map(TimeEntryEntity::toDomain);
    }

    @Override
    public List<TimeEntry> findByWorkOrder(UUID workOrderId) {
        return jpa.findByWorkOrderIdOrderByStartedAtDescIdDesc(workOrderId).stream()
                .map(TimeEntryEntity::toDomain).toList();
    }

    @Override
    public Optional<TimeEntry> findRunningByMechanic(UUID mechanicId) {
        return jpa.findByMechanicIdAndStoppedAtIsNull(mechanicId).map(TimeEntryEntity::toDomain);
    }

    @Override
    public List<TimeEntry> findRunningByWorkOrder(UUID workOrderId) {
        return jpa.findByWorkOrderIdAndStoppedAtIsNull(workOrderId).stream().map(TimeEntryEntity::toDomain).toList();
    }
}
