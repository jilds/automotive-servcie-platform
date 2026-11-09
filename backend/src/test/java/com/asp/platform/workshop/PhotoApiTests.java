package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/// Photos of a work order over the API: the type decided by the first bytes, the 5 MB and 30-per-order limits, the
/// ETag, who may add and change them, and that another workshop gets 404 (docs/api/release-4.md "Workshop floor").
class PhotoApiTests extends FloorTestSupport {

    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F');
    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00);
    private static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P');

    @Test
    void should_store_jpeg_png_and_webp_by_their_first_bytes_whatever_the_client_declares() throws Exception {
        Shop g = garage("Fotos Tipos");
        Mechanic rui = mechanic(g);
        String wo = orderOf(g, rui);

        upload(rui.token(), wo, "frente.jpg", "text/plain", JPEG, "CHECK_IN", "Frente")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.stage").value("CHECK_IN"))
                .andExpect(jsonPath("$.caption").value("Frente"))
                .andExpect(jsonPath("$.sizeBytes").value(JPEG.length))
                .andExpect(jsonPath("$.uploadedByName").value("mechanic"))
                .andExpect(jsonPath("$.uploadedById").value(rui.id()))
                .andExpect(jsonPath("$.workOrderId").value(wo))
                .andExpect(jsonPath("$.version").value(0));
        upload(rui.token(), wo, "a.bin", "application/octet-stream", PNG, "WORK", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.caption").isEmpty());
        upload(rui.token(), wo, "b.jpg", "image/jpeg", WEBP, "DELIVERY", "  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.caption").isEmpty());

        call(get("/api/v1/work-orders/" + wo + "/photos"), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$[2].stage").value("DELIVERY"));
    }

    @Test
    void should_refuse_what_is_not_a_jpeg_png_or_webp_even_when_declared_as_one() throws Exception {
        Shop g = garage("Fotos Recusa");
        String wo = orderOf(g, mechanic(g));
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        byte[] html = "<html><body>x</body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] riffWave = bytes('R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E');
        byte[] truncatedPng = bytes(0x89, 'P', 'N', 'G');
        for (byte[] bad : new byte[][] {svg, gif, html, riffWave, truncatedPng, bytes(0xFF, 0xD8)}) {
            upload(g.owner(), wo, "x.jpg", "image/jpeg", bad, "WORK", null)
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("photo-type-not-allowed"));
        }
        upload(g.owner(), wo, "x.jpg", "image/jpeg", new byte[0], "WORK", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.file").exists());
        upload(g.owner(), wo, "x.jpg", "image/jpeg", JPEG, "WORK", "c".repeat(201))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.caption").exists());
        call(get("/api/v1/work-orders/" + wo + "/photos"), g.owner()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_accept_five_megabytes_and_refuse_one_byte_more() throws Exception {
        Shop g = garage("Fotos Tamanho");
        String wo = orderOf(g, mechanic(g));
        byte[] exactly = Arrays.copyOf(JPEG, 5 * 1024 * 1024);
        byte[] tooBig = Arrays.copyOf(JPEG, 5 * 1024 * 1024 + 1);

        upload(g.owner(), wo, "grande.jpg", "image/jpeg", exactly, "WORK", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(5 * 1024 * 1024));
        upload(g.owner(), wo, "enorme.jpg", "image/jpeg", tooBig, "WORK", null)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("photo-too-large"));
        call(get("/api/v1/work-orders/" + wo + "/photos"), g.owner()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_keep_at_most_thirty_photos_per_order() throws Exception {
        Shop g = garage("Fotos Limite");
        String wo = orderOf(g, mechanic(g));
        String other = orderOf(g, mechanic(g));
        for (int i = 0; i < 30; i++) {
            upload(g.owner(), wo, "f" + i + ".jpg", "image/jpeg", JPEG, "WORK", null).andExpect(status().isCreated());
        }
        upload(g.owner(), wo, "f31.jpg", "image/jpeg", JPEG, "WORK", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("photo-limit-reached"));
        // The limit is per order, and deleting one makes room.
        upload(g.owner(), other, "f.jpg", "image/jpeg", JPEG, "WORK", null).andExpect(status().isCreated());
        String first = call(get("/api/v1/work-orders/" + wo + "/photos"), g.owner()).andReturn().getResponse()
                .getContentAsString();
        String firstId = com.jayway.jsonpath.JsonPath.read(first, "$[0].id");
        call(delete("/api/v1/work-orders/" + wo + "/photos/" + firstId).content("{\"reason\":\"Duplicada\"}"), g.owner())
                .andExpect(status().isNoContent());
        upload(g.owner(), wo, "f32.jpg", "image/jpeg", JPEG, "WORK", null).andExpect(status().isCreated());
    }

    @Test
    void should_serve_the_bytes_with_an_etag_and_answer_not_modified() throws Exception {
        Shop g = garage("Fotos Conteudo");
        Mechanic rui = mechanic(g);
        String wo = orderOf(g, rui);
        String photo = idIn(upload(rui.token(), wo, "f.png", "image/png", PNG, "WORK", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String path = "/api/v1/work-orders/" + wo + "/photos/" + photo + "/content";

        String etag = call(get(path), rui.token())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-cache, private"))
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).matches("\"[0-9a-f]{64}\"");
        assertThat(call(get(path), g.owner()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(PNG);

        call(get(path).header("If-None-Match", etag), rui.token())
                .andExpect(status().isNotModified());
        call(get(path).header("If-None-Match", "\"other\""), rui.token()).andExpect(status().isOk());
        // The bytes need a token.
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void should_let_the_assigned_mechanic_add_photos_and_change_only_their_own() throws Exception {
        Shop g = garage("Fotos Permissoes");
        Mechanic rui = mechanic(g);
        Mechanic ze = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String wo = orderOf(g, rui);

        upload(ze.token(), wo, "f.jpg", "image/jpeg", JPEG, "WORK", null).andExpect(status().isForbidden());
        String deskPhoto = idIn(upload(desk, wo, "d.jpg", "image/jpeg", JPEG, "CHECK_IN", "Balcao")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String ruiPhoto = idIn(upload(rui.token(), wo, "r.jpg", "image/jpeg", JPEG, "WORK", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String base = "/api/v1/work-orders/" + wo + "/photos/";

        // Everyone reads, also a mechanic who is not assigned.
        call(get("/api/v1/work-orders/" + wo + "/photos"), ze.token()).andExpect(jsonPath("$.length()").value(2));
        call(get(base + ruiPhoto + "/content"), ze.token()).andExpect(status().isOk());

        // A mechanic changes only the photos they added to their own jobs.
        call(put(base + deskPhoto).content("{\"stage\":\"WORK\",\"caption\":\"x\",\"version\":0}"), rui.token())
                .andExpect(status().isForbidden());
        call(delete(base + deskPhoto).content("{\"reason\":\"Duplicada\"}"), rui.token()).andExpect(status().isForbidden());
        call(put(base + ruiPhoto).content("{\"stage\":\"DELIVERY\",\"caption\":\"Depois\",\"version\":0}"),
                ze.token()).andExpect(status().isForbidden());
        call(put(base + ruiPhoto).content("{\"stage\":\"DELIVERY\",\"caption\":\"Depois\",\"version\":0}"),
                rui.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DELIVERY"))
                .andExpect(jsonPath("$.caption").value("Depois"))
                .andExpect(jsonPath("$.version").value(1));
        call(put(base + ruiPhoto).content("{\"stage\":\"WORK\",\"caption\":null,\"version\":0}"), rui.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(delete(base + ruiPhoto).content("{\"reason\":\"Duplicada\"}"), rui.token()).andExpect(status().isNoContent());
        // The desk and the owner manage every photo.
        call(put(base + deskPhoto).content("{\"stage\":\"WORK\",\"caption\":null,\"version\":0}"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").isEmpty());
        call(delete(base + deskPhoto).content("{\"reason\":\"Duplicada\"}"), g.owner()).andExpect(status().isNoContent());
        call(get(base + deskPhoto + "/content"), g.owner())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("photo-not-found"));
    }

    @Test
    void should_answer_404_for_photos_of_another_order_or_workshop() throws Exception {
        Shop a = garage("Fotos A");
        Shop b = garage("Fotos B");
        String wo = orderOf(a, mechanic(a));
        String otherOrder = orderOf(a, mechanic(a));
        String photo = idIn(upload(a.owner(), wo, "f.jpg", "image/jpeg", JPEG, "WORK", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String path = "/api/v1/work-orders/" + wo + "/photos/" + photo;

        // Workshop B cannot see the order, the list, the bytes, or change or delete the photo.
        call(get("/api/v1/work-orders/" + wo + "/photos"), b.owner())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("work-order-not-found"));
        call(get(path + "/content"), b.owner())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("photo-not-found"));
        call(put(path).content("{\"stage\":\"WORK\",\"caption\":\"x\",\"version\":0}"), b.owner())
                .andExpect(status().isNotFound());
        call(delete(path).content("{\"reason\":\"Duplicada\"}"), b.owner()).andExpect(status().isNotFound());
        upload(b.owner(), wo, "f.jpg", "image/jpeg", JPEG, "WORK", null).andExpect(status().isNotFound());
        // The photo is not reachable through another order of the same workshop either.
        call(get("/api/v1/work-orders/" + otherOrder + "/photos/" + photo + "/content"), a.owner())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("photo-not-found"));
        call(get(path + "/content"), a.owner()).andExpect(status().isOk());
    }

    private ResultActions upload(String token, String wo, String filename, String declaredType, byte[] bytes,
            String stage, String caption) throws Exception {
        var request = multipart("/api/v1/work-orders/" + wo + "/photos")
                .file(new MockMultipartFile("file", filename, declaredType, bytes))
                .param("stage", stage)
                .header("Authorization", "Bearer " + token);
        if (caption != null) {
            request.param("caption", caption);
        }
        return mvc.perform(request);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
