package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/// Row-level security on every table of V19 (ADR 0002): a second workshop sees none of the first workshop's rows, in
/// SQL as the app role, and cannot write rows for it.
class WorkshopFloorIsolationTests extends FloorTestSupport {

    private static final List<String> TABLES = List.of("work_time_entry", "work_order_photo",
            "work_order_photo_data", "checklist_template", "checklist_template_item", "work_order_checklist",
            "work_order_checklist_item", "work_time_entry_change");

    @Test
    void should_hide_every_floor_table_from_another_workshop() throws Exception {
        Shop a = garage("Chao A");
        Shop b = garage("Chao B");
        Mechanic rui = mechanic(a);
        String wo = orderOf(a, rui);
        startTimer(rui.token(), wo);
        String entry = com.jayway.jsonpath.JsonPath.read(timeSummary(a.owner(), wo), "$.entries[0].id");
        call(put("/api/v1/work-orders/" + wo + "/time-entries/" + entry).content(
                "{\"startedAt\":\"%s\",\"stoppedAt\":null,\"reason\":\"Corrigido\",\"version\":0}"
                        .formatted(java.time.Instant.now().minusSeconds(60))), a.owner()).andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/work-orders/" + wo + "/photos")
                .file(new MockMultipartFile("file", "f.jpg", "image/jpeg",
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}))
                .param("stage", "WORK").header("Authorization", "Bearer " + a.owner()))
                .andExpect(status().isCreated());
        call(put("/api/v1/checklist-templates/DELIVERY").content("""
                {"items":[{"label":"Um","required":true},{"label":"Dois","required":false}],"version":0}"""),
                a.owner()).andExpect(status().isOk());
        call(post("/api/v1/work-orders/" + wo + "/checklists/DELIVERY"), a.owner()).andExpect(status().isCreated());

        for (String table : TABLES) {
            assertThat(count(a, table)).as("%s rows seen by the workshop that owns them", table).isPositive();
            assertThat(count(b, table)).as("%s rows seen by another workshop", table).isZero();
        }
        // No session setting: no rows either.
        for (String table : TABLES) {
            assertThat(jdbc.execute((java.sql.Connection connection) -> scalar(connection, table)))
                    .as("%s rows without a workshop", table).isZero();
        }
    }

    @Test
    void should_refuse_a_row_written_for_another_workshop() throws Exception {
        Shop a = garage("Chao Escrita A");
        Shop b = garage("Chao Escrita B");
        assertThatThrownBy(() -> asWorkshop(b, connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into checklist_template (id, tenant_id, kind, created_at, updated_at)
                    values (?::uuid, ?::uuid, 'CHECK_IN', now(), now())""")) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setString(2, a.id());
                return insert.executeUpdate();
            }
        })).rootCause().hasMessageContaining("row-level security");
    }

    private int count(Shop shop, String table) {
        return asWorkshop(shop, connection -> scalar(connection, table));
    }

    private static int scalar(java.sql.Connection connection, String table) throws java.sql.SQLException {
        try (PreparedStatement select = connection.prepareStatement("select count(*) from " + table);
                ResultSet rs = select.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
