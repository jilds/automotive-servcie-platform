package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/// Photo rules added after the first review: the DOCUMENTS stage (owner and front desk only), soft delete with a
/// reason and its audit, EXIF removal, and the headers of the content answer.
class PhotoRulesApiTests extends FloorTestSupport {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};

    @Test
    void should_keep_documents_photos_away_from_mechanics() throws Exception {
        Shop g = garage("Docs Fotos");
        Mechanic rui = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String wo = orderOf(g, rui);
        String base = "/api/v1/work-orders/" + wo + "/photos";

        upload(rui.token(), wo, "DOCUMENTS").andExpect(status().isForbidden());
        String doc = idIn(upload(desk, wo, "DOCUMENTS").andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("DOCUMENTS")).andReturn().getResponse().getContentAsString());
        String work = idIn(upload(rui.token(), wo, "WORK").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString());

        call(get(base), rui.token()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(work));
        call(get(base), desk).andExpect(jsonPath("$.length()").value(2));
        call(get(base), g.owner()).andExpect(jsonPath("$.length()").value(2));
        call(get(base + "/" + doc + "/content"), rui.token()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("photo-not-found"));
        call(get(base + "/" + doc + "/content"), desk).andExpect(status().isOk());
        call(put(base + "/" + doc).content("{\"stage\":\"WORK\",\"caption\":null,\"version\":0}"), rui.token())
                .andExpect(status().isNotFound());
        call(delete(base + "/" + doc).content("{\"reason\":\"x\"}"), rui.token()).andExpect(status().isNotFound());
        // A mechanic cannot move a photo into DOCUMENTS either.
        call(put(base + "/" + work).content("{\"stage\":\"DOCUMENTS\",\"caption\":null,\"version\":0}"), rui.token())
                .andExpect(status().isForbidden());
        call(put(base + "/" + work).content("{\"stage\":\"DOCUMENTS\",\"caption\":null,\"version\":0}"), desk)
                .andExpect(status().isOk());
        call(get(base), rui.token()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_soft_delete_with_a_reason_and_keep_the_audit() throws Exception {
        Shop g = garage("Fotos Apagar");
        Mechanic rui = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String wo = orderOf(g, rui);
        String photo = idIn(upload(rui.token(), wo, "WORK").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString());
        String path = "/api/v1/work-orders/" + wo + "/photos/" + photo;

        call(delete(path), rui.token()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reason-required")).andExpect(jsonPath("$.errors.reason").exists());
        call(delete(path).content("{\"reason\":\"   \"}"), rui.token()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reason-required"));
        call(delete(path).content("{\"reason\":\"%s\"}".formatted("r".repeat(501))), rui.token())
                .andExpect(status().isBadRequest());
        call(delete(path).content("{\"reason\":\"  Foto desfocada  \"}"), rui.token())
                .andExpect(status().isNoContent());

        // Gone for everyone; the second delete finds nothing.
        call(get("/api/v1/work-orders/" + wo + "/photos"), g.owner()).andExpect(jsonPath("$.length()").value(0));
        call(get(path + "/content"), g.owner()).andExpect(status().isNotFound());
        call(put(path).content("{\"stage\":\"WORK\",\"caption\":null,\"version\":0}"), desk)
                .andExpect(status().isNotFound());
        call(delete(path).content("{\"reason\":\"x\"}"), desk).andExpect(status().isNotFound());

        // The record stays with who, when and why, and the bytes stay until the purge.
        assertThat(this.<String>asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    select p.delete_reason || '|' || (p.deleted_by = ?::uuid) || '|' || (p.deleted_at is not null)
                           || '|' || (p.purged_at is null) || '|' || (select count(*) from work_order_photo_data d
                                                                     where d.photo_id = p.id)
                    from work_order_photo p where p.id = ?::uuid""")) {
                s.setString(1, rui.id());
                s.setString(2, photo);
                try (ResultSet rs = s.executeQuery()) {
                    rs.next();
                    return rs.getString(1);
                }
            }
        })).isEqualTo("Foto desfocada|true|true|true|1");
    }

    @Test
    void should_let_only_the_owner_delete_photos_of_a_delivered_or_cancelled_order() throws Exception {
        Shop g = garage("Fotos Fechadas");
        Mechanic rui = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String delivered = orderOf(g, rui);
        String cancelled = orderOf(g, rui);
        String p1 = idIn(upload(desk, delivered, "DELIVERY").andReturn().getResponse().getContentAsString());
        String p2 = idIn(upload(desk, cancelled, "WORK").andReturn().getResponse().getContentAsString());
        String p3 = idIn(upload(rui.token(), delivered, "WORK").andReturn().getResponse().getContentAsString());
        moveOk(g.owner(), delivered, "IN_PROGRESS");
        moveOk(g.owner(), delivered, "READY");
        moveOk(g.owner(), delivered, "DELIVERED");
        moveOk(g.owner(), cancelled, "CANCELLED");

        String reason = "{\"reason\":\"Engano\"}";
        call(delete("/api/v1/work-orders/" + delivered + "/photos/" + p1).content(reason), desk)
                .andExpect(status().isForbidden());
        call(delete("/api/v1/work-orders/" + delivered + "/photos/" + p3).content(reason), rui.token())
                .andExpect(status().isForbidden());
        call(delete("/api/v1/work-orders/" + cancelled + "/photos/" + p2).content(reason), desk)
                .andExpect(status().isForbidden());
        call(delete("/api/v1/work-orders/" + delivered + "/photos/" + p1).content(reason), g.owner())
                .andExpect(status().isNoContent());
        call(delete("/api/v1/work-orders/" + cancelled + "/photos/" + p2).content(reason), g.owner())
                .andExpect(status().isNoContent());
    }

    @Test
    void should_store_the_upload_without_exif_and_serve_it_with_safe_headers() throws Exception {
        Shop g = garage("Fotos Exif");
        String wo = orderOf(g, mechanic(g));
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        jpeg.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8});
        byte[] exif = "Exif\0\0GPS-38.7N".getBytes(StandardCharsets.ISO_8859_1);
        jpeg.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xE1, 0, (byte) (exif.length + 2)});
        jpeg.writeBytes(exif);
        jpeg.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xDA, 0, 2, 0x55, 0x66});
        byte[] original = jpeg.toByteArray();

        String body = mvc.perform(multipart("/api/v1/work-orders/" + wo + "/photos")
                .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", original)).param("stage", "WORK")
                .header("Authorization", "Bearer " + g.owner()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(original.length - exif.length - 4))
                .andReturn().getResponse().getContentAsString();
        String id = idIn(body);

        var response = call(get("/api/v1/work-orders/" + wo + "/photos/" + id + "/content"), g.owner())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"photo-" + id + ".jpg\""))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.ISO_8859_1))
                .doesNotContain("Exif").doesNotContain("GPS");
        assertThat(response.getContentAsByteArray()).hasSize(original.length - exif.length - 4);
        // The ETag is the hash of the stored bytes.
        assertThat(response.getHeader("ETag")).hasSize(66);
    }

    private ResultActions upload(String token, String wo, String stage) throws Exception {
        return mvc.perform(multipart("/api/v1/work-orders/" + wo + "/photos")
                .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", JPEG)).param("stage", stage)
                .header("Authorization", "Bearer " + token));
    }
}
