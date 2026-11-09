package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/// The column-level grants of V19 for the app role (`asp_app`): what the application never does, the database refuses
/// (permission denied), and what it does still works.
class WorkshopFloorGrantsTests extends FloorTestSupport {

    @Test
    void should_refuse_to_the_app_role_what_the_application_never_does() throws Exception {
        Shop g = garage("Chao Permissoes");
        Mechanic rui = mechanic(g);
        String wo = orderOf(g, rui);
        startTimer(rui.token(), wo);
        mvc.perform(multipart("/api/v1/work-orders/" + wo + "/photos")
                .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8,
                        (byte) 0xFF, 1}))
                .param("stage", "WORK").header("Authorization", "Bearer " + g.owner()))
                .andExpect(status().isCreated());
        call(post("/api/v1/work-orders/" + wo + "/checklists/CHECK_IN"), g.owner()).andExpect(status().isCreated());
        call(put("/api/v1/checklist-templates/CHECK_IN").content(
                "{\"items\":[{\"label\":\"Um\",\"required\":true}],\"version\":0}"), g.owner())
                .andExpect(status().isOk());

        List<String> refused = List.of(
                "delete from work_time_entry",
                "update work_time_entry set mechanic_id = mechanic_id",
                "update work_time_entry set work_order_id = work_order_id",
                "delete from work_time_entry_change",
                "update work_time_entry_change set reason = 'x'",
                "delete from work_order_photo",
                "update work_order_photo set size_bytes = 1",
                "update work_order_photo set sha256 = sha256",
                "update work_order_photo_data set data = data",
                "delete from checklist_template",
                "update checklist_template set kind = kind",
                "update checklist_template_item set label = 'x'",
                "delete from work_order_checklist",
                "update work_order_checklist set started_by = started_by",
                "delete from work_order_checklist_item",
                "update work_order_checklist_item set label = 'x'",
                "update work_order_checklist_item set required = not required");
        for (String sql : refused) {
            assertThatThrownBy(() -> asWorkshop(g, c -> {
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    return s.executeUpdate();
                }
            })).as(sql).rootCause().hasMessageContaining("permission denied");
        }

        // What the application does stays allowed.
        List<String> allowed = List.of(
                "update work_time_entry set stopped_at = now(), auto_stopped = false, version = version",
                "update work_order_photo set caption = 'x', stage = 'WORK', deleted_at = null, purged_at = null",
                "delete from work_order_photo_data where false",
                "update work_order_checklist_item set result = null, note = null, answered_by = null, "
                        + "answered_at = null",
                "update checklist_template set version = version, updated_at = updated_at");
        for (String sql : allowed) {
            int rows = asWorkshop(g, c -> {
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    return s.executeUpdate();
                }
            });
            assertThat(rows).as(sql).isGreaterThanOrEqualTo(0);
        }
    }
}
