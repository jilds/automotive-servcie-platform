package com.asp.platform.workshop.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.workshop.domain.ChecklistItem;
import com.asp.platform.workshop.domain.ChecklistKind;
import com.asp.platform.workshop.domain.ChecklistResult;
import com.asp.platform.workshop.domain.WorkOrderChecklist;
import com.asp.platform.workshop.domain.WorkOrderChecklistRepository;

/// Plain SQL for the checklists of a work order: the checklist and its items are written together when it starts;
/// afterwards only the answers of the items change. Row-level security limits every statement to the current
/// workshop and `tenant_id` comes from the connection.
@Repository
class JdbcWorkOrderChecklistRepository implements WorkOrderChecklistRepository {

    private final JdbcClient jdbc;

    JdbcWorkOrderChecklistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WorkOrderChecklist saveNew(WorkOrderChecklist checklist) {
        insert(checklist);
        return checklist;
    }

    @Override
    public void saveAnswer(ChecklistItem item) {
        ChecklistResult result = item.result();
        Instant answeredAt = item.answeredAt();
        jdbc.sql("""
                update work_order_checklist_item
                set result = :result, note = :note, answered_by = :by, answered_at = :at
                where id = :id""")
                .param("id", item.id())
                .param("result", result == null ? null : result.name(), Types.VARCHAR)
                .param("note", item.note(), Types.VARCHAR)
                .param("by", item.answeredBy())
                .param("at", answeredAt == null ? null : utc(answeredAt), Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    @Override
    public Optional<WorkOrderChecklist> find(UUID workOrderId, ChecklistKind kind) {
        return findByWorkOrder(workOrderId).stream().filter(c -> c.kind() == kind).findFirst();
    }

    @Override
    public List<WorkOrderChecklist> findByWorkOrder(UUID workOrderId) {
        List<Header> headers = jdbc.sql("""
                select id, kind, started_at, started_by from work_order_checklist
                where work_order_id = :order order by kind""")
                .param("order", workOrderId)
                .query((rs, _) -> new Header(rs.getObject("id", UUID.class),
                        ChecklistKind.valueOf(rs.getString("kind")), instant(rs, "started_at"),
                        rs.getObject("started_by", UUID.class)))
                .list();
        if (headers.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ChecklistItem>> items = new LinkedHashMap<>();
        jdbc.sql("""
                select id, checklist_id, position, label, required, result, note, answered_by, answered_at
                from work_order_checklist_item where checklist_id in (:ids) order by checklist_id, position""")
                .param("ids", headers.stream().map(Header::id).toList())
                .query((rs, _) -> {
                    String result = rs.getString("result");
                    items.computeIfAbsent(rs.getObject("checklist_id", UUID.class), _ -> new ArrayList<>())
                            .add(new ChecklistItem(rs.getObject("id", UUID.class), rs.getInt("position"),
                                    rs.getString("label"), rs.getBoolean("required"),
                                    result == null ? null : ChecklistResult.valueOf(result), rs.getString("note"),
                                    rs.getObject("answered_by", UUID.class), nullableInstant(rs, "answered_at")));
                    return 0;
                })
                .list();
        return headers.stream()
                .map(h -> new WorkOrderChecklist(h.id(), workOrderId, h.kind(), h.startedAt(), h.startedBy(),
                        items.getOrDefault(h.id(), List.of())))
                .toList();
    }

    private void insert(WorkOrderChecklist checklist) {
        jdbc.sql("""
                insert into work_order_checklist (id, work_order_id, kind, started_at, started_by)
                values (:id, :order, :kind, :startedAt, :startedBy)""")
                .param("id", checklist.id())
                .param("order", checklist.workOrderId())
                .param("kind", checklist.kind().name())
                .param("startedAt", utc(checklist.startedAt()))
                .param("startedBy", checklist.startedBy())
                .update();
        for (ChecklistItem item : checklist.items()) {
            jdbc.sql("""
                    insert into work_order_checklist_item (id, checklist_id, position, label, required)
                    values (:id, :checklist, :position, :label, :required)""")
                    .param("id", item.id())
                    .param("checklist", checklist.id())
                    .param("position", item.position())
                    .param("label", item.label())
                    .param("required", item.required())
                    .update();
        }
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

    private record Header(UUID id, ChecklistKind kind, Instant startedAt, UUID startedBy) {
    }
}
