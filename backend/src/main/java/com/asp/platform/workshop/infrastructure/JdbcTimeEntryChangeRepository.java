package com.asp.platform.workshop.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.workshop.domain.TimeEntryChange;
import com.asp.platform.workshop.domain.TimeEntryChangeRepository;

/// Insert-only history of time corrections (`work_time_entry_change`, V19). Row-level security limits every
/// statement to the current workshop and `tenant_id` comes from the connection.
@Repository
class JdbcTimeEntryChangeRepository implements TimeEntryChangeRepository {

    private final JdbcClient jdbc;

    JdbcTimeEntryChangeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TimeEntryChange save(TimeEntryChange change) {
        jdbc.sql("""
                insert into work_time_entry_change
                    (id, entry_id, old_started_at, old_stopped_at, old_line_id, changed_by, changed_at, reason)
                values (:id, :entry, :oldStart, :oldStop, :oldLine, :by, :at, :reason)""")
                .param("id", change.id())
                .param("entry", change.entryId())
                .param("oldStart", utc(change.oldStartedAt()))
                .param("oldStop", change.oldStoppedAt() == null ? null : utc(change.oldStoppedAt()),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .param("oldLine", change.oldLineId(), Types.OTHER)
                .param("by", change.changedBy())
                .param("at", utc(change.changedAt()))
                .param("reason", change.reason())
                .update();
        return change;
    }

    @Override
    public List<TimeEntryChange> findByEntry(UUID entryId) {
        return jdbc.sql("""
                select id, entry_id, old_started_at, old_stopped_at, old_line_id, changed_by, changed_at, reason
                from work_time_entry_change where entry_id = :entry order by changed_at, id""")
                .param("entry", entryId)
                .query((rs, _) -> new TimeEntryChange(rs.getObject("id", UUID.class),
                        rs.getObject("entry_id", UUID.class), instant(rs, "old_started_at"),
                        nullableInstant(rs, "old_stopped_at"), rs.getObject("old_line_id", UUID.class),
                        rs.getObject("changed_by", UUID.class), instant(rs, "changed_at"), rs.getString("reason")))
                .list();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
