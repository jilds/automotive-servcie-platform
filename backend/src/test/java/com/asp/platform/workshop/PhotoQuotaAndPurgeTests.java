package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.workshop.application.PhotoPurge;

/// The per-workshop photo storage quota (here 25 bytes) and the purge job that drops the bytes of deleted photos
/// after the retention period (30 days), called directly as the other scheduled jobs are in tests.
@TestPropertySource(properties = "app.photos.max-bytes-per-workshop=25")
class PhotoQuotaAndPurgeTests extends FloorTestSupport {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5, 6, 7}; // 10 bytes

    @Autowired
    PhotoPurge purge;

    @Test
    void should_refuse_photos_beyond_the_workshop_quota_and_free_it_when_the_bytes_are_purged() throws Exception {
        Shop g = garage("Quota Fotos");
        Shop other = garage("Quota Outra");
        String wo = orderOf(g, mechanic(g));
        String first = idIn(upload(g.owner(), wo).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString());
        upload(g.owner(), wo).andExpect(status().isCreated());
        upload(g.owner(), wo).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("photo-storage-full"));
        // Another workshop has its own quota.
        upload(other.owner(), orderOf(other, mechanic(other))).andExpect(status().isCreated());

        // A deleted photo still counts until its bytes are purged.
        call(delete("/api/v1/work-orders/" + wo + "/photos/" + first).content("{\"reason\":\"Repetida\"}"), g.owner())
                .andExpect(status().isNoContent());
        upload(g.owner(), wo).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("photo-storage-full"));

        // Not yet: the retention period is 30 days. After it the job drops the bytes and keeps the audit row.
        assertThat(purge.purgeDue(Instant.now().plus(Duration.ofDays(29)))).isZero();
        assertThat(bytesRows(g)).isEqualTo(2);
        // A suspended workshop is purged too: its deleted photos must not keep their bytes for ever.
        String admin = platformAdmin();
        call(put("/api/v1/admin/workshops/" + g.id() + "/subscription").content(withVersion(
                "{\"plan\":\"PRO\",\"status\":\"SUSPENDED\"}", versionAt(admin, "/api/v1/admin/workshops/" + g.id()))),
                admin).andExpect(status().isOk());
        assertThat(purge.purgeDue(Instant.now().plus(Duration.ofDays(31)))).isEqualTo(1);
        assertThat(bytesRows(g)).isEqualTo(1);
        assertThat(purgedRows(g)).isEqualTo(1);
        assertThat(photoRows(g)).isEqualTo(2);
        // Running it again finds nothing, and the space is free again.
        assertThat(purge.purgeDue(Instant.now().plus(Duration.ofDays(31)))).isZero();
        call(put("/api/v1/admin/workshops/" + g.id() + "/subscription").content(withVersion(
                "{\"plan\":\"PRO\",\"status\":\"ACTIVE\"}", versionAt(admin, "/api/v1/admin/workshops/" + g.id()))),
                admin).andExpect(status().isOk());
        upload(g.owner(), wo).andExpect(status().isCreated());
    }

    private ResultActions upload(String token, String wo) throws Exception {
        return mvc.perform(multipart("/api/v1/work-orders/" + wo + "/photos")
                .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", JPEG)).param("stage", "WORK")
                .header("Authorization", "Bearer " + token));
    }

    private int bytesRows(Shop g) {
        return scalar(g, "select count(*) from work_order_photo_data");
    }

    private int purgedRows(Shop g) {
        return scalar(g, "select count(*) from work_order_photo where purged_at is not null");
    }

    private int photoRows(Shop g) {
        return scalar(g, "select count(*) from work_order_photo");
    }

    private int scalar(Shop g, String sql) {
        return asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement(sql); ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }
}
