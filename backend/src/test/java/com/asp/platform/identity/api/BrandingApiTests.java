package com.asp.platform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Per-workshop themes: public read by slug (presentation only), owner and platform admin changes, logo checks.
class BrandingApiTests extends ApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H',
            'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0};
    private static final String SAFE_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
              <defs><linearGradient id="g"><stop offset="0" stop-color="#0d6efd"/></linearGradient></defs>
              <rect width="10" height="10" fill="url(#g)"/><title>Logo</title>
            </svg>""";

    @Test
    void should_save_the_owners_colours_and_pick_a_readable_button_text() throws Exception {
        String owner = signup("Oficina Cores");

        call(put("/api/v1/workshop/branding").content(withVersion("""
                {"displayName":"  Cores & Filhos  ","primaryColor":"#0B3D91","accentColor":"#ffcc00"}""",
                versionAt(owner, "/api/v1/branding"))), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cores & Filhos"))
                .andExpect(jsonPath("$.displayName").value("Cores & Filhos"))
                .andExpect(jsonPath("$.primaryColor").value("#0b3d91"))
                .andExpect(jsonPath("$.primaryTextColor").value("#ffffff"))
                .andExpect(jsonPath("$.accentColor").value("#ffcc00"))
                .andExpect(jsonPath("$.logoUrl").isEmpty());

        // A light primary colour gets black text: white would not reach 4.5:1.
        call(put("/api/v1/workshop/branding").content(withVersion("{\"primaryColor\":\"#ffd43b\"}",
                versionAt(owner, "/api/v1/branding"))), owner)
                .andExpect(jsonPath("$.primaryTextColor").value("#000000"))
                .andExpect(jsonPath("$.displayName").isEmpty())
                .andExpect(jsonPath("$.name").value("Oficina Cores"));

        // Every workshop member reads the theme to apply it.
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        call(get("/api/v1/branding"), login(mechanicEmail, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#ffd43b"));
    }

    @Test
    void should_reject_bad_colours_and_long_names() throws Exception {
        String owner = signup("Oficina Erros");

        call(put("/api/v1/workshop/branding").content(withVersion("""
                {"displayName":"%s","primaryColor":"red","accentColor":"#12345"}""".formatted("x".repeat(81)), versionAt(owner, "/api/v1/branding"))), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.displayName").exists())
                .andExpect(jsonPath("$.errors.primaryColor").exists())
                .andExpect(jsonPath("$.errors.accentColor").exists());
    }

    @Test
    void should_let_only_the_owner_change_the_theme() throws Exception {
        String owner = signup("Oficina Dono");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String desk = login(deskEmail, PASSWORD);

        call(put("/api/v1/workshop/branding").content(withVersion("{\"primaryColor\":\"#000000\"}",
                versionAt(desk, "/api/v1/branding"))), desk)
                .andExpect(status().isForbidden());
        uploadLogo("/api/v1/workshop/branding/logo", desk, "logo.png", PNG).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/workshop/branding").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_change_only_the_owners_own_workshop() throws Exception {
        String a = signup("Oficina Tema A");
        String b = signup("Oficina Tema B");
        String slugB = JsonPath.read(call(get("/api/v1/branding"), b).andReturn().getResponse().getContentAsString(),
                "$.slug");

        call(put("/api/v1/workshop/branding").content(withVersion("{\"primaryColor\":\"#123456\"}",
                versionAt(a, "/api/v1/branding"))), a).andExpect(status().isOk());
        uploadLogo("/api/v1/workshop/branding/logo", a, "logo.png", PNG).andExpect(status().isOk());

        mvc.perform(get("/api/v1/branding/" + slugB))
                .andExpect(jsonPath("$.primaryColor").isEmpty())
                .andExpect(jsonPath("$.logoUrl").isEmpty());
        call(get("/api/v1/branding"), b).andExpect(jsonPath("$.primaryColor").isEmpty());
    }

    @Test
    void should_expose_only_presentation_on_the_public_endpoint() throws Exception {
        String owner = signup("Oficina Pública");
        call(put("/api/v1/workshop/branding").content(withVersion("{\"primaryColor\":\"#0b3d91\"}",
                versionAt(owner, "/api/v1/branding"))), owner);
        String slug = JsonPath.read(call(get("/api/v1/branding"), owner).andReturn().getResponse()
                .getContentAsString(), "$.slug");

        String body = mvc.perform(get("/api/v1/branding/" + slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> fields = JsonPath.read(body, "$");
        // `version` is the optimistic-locking counter the theme form sends back; it reveals nothing.
        assertThat(fields).containsOnlyKeys("slug", "name", "displayName", "primaryColor", "primaryTextColor",
                "accentColor", "logoUrl", "privacyUrl", "version");
        // The platform's privacy policy (app.site.privacy-url), the same for every workshop.
        assertThat((String) JsonPath.read(body, "$.privacyUrl")).isEqualTo("https://asp.test/privacidade");
        assertThat(body).doesNotContain("tenant").doesNotContain("@");

        mvc.perform(get("/api/v1/branding/no-such-workshop"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("workshop-not-found"));
        mvc.perform(get("/api/v1/branding/Bad..Slug")).andExpect(status().isNotFound());
    }

    @Test
    void should_serve_the_logo_with_etag_and_cache_headers() throws Exception {
        String owner = signup("Oficina Logótipo");

        String logoUrl = JsonPath.read(uploadLogo("/api/v1/workshop/branding/logo", owner, "logo.png", PNG)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.logoUrl");
        assertThat(logoUrl).matches("/api/v1/branding/[a-z0-9-]+/logo\\?v=[0-9a-f]{12}");

        String etag = mvc.perform(get(logoUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("ETag", startsWith("\"")))
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(get(logoUrl).header("If-None-Match", etag)).andExpect(status().isNotModified());

        call(delete("/api/v1/workshop/branding/logo"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").isEmpty());
        mvc.perform(get(logoUrl))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("logo-not-found"));
    }

    @Test
    void should_accept_a_plain_svg_and_serve_it_sandboxed() throws Exception {
        String owner = signup("Oficina SVG");

        String logoUrl = JsonPath.read(uploadLogo("/api/v1/workshop/branding/logo", owner, "logo.svg",
                SAFE_SVG.getBytes(StandardCharsets.UTF_8)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.logoUrl");

        mvc.perform(get(logoUrl))
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));
    }

    @Test
    void should_refuse_malicious_svgs() throws Exception {
        String owner = signup("Oficina Ataque");
        String[] attacks = {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect OnClick=\"alert(1)\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:x=\"http://www.w3.org/1999/xlink\">"
                        + "<use x:href=\"https://evil.example/a.svg#x\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><use href=\"javascript:alert(1)\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><foreignObject><div>x</div></foreignObject></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"https://evil.example/x.png\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><style>@import url(https://evil.example/x.css);</style></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect style=\"fill:url(https://evil.example/x)\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><a href=\"https://evil.example\"><rect/></a></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><set attributeName=\"onmouseover\" to=\"alert(1)\"/></svg>",
                "<?xml-stylesheet href=\"https://evil.example/x.css\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>",
                "<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg xmlns=\"http://www.w3.org/2000/svg\">"
                        + "<text>&x;</text></svg>",
        };
        for (String svg : attacks) {
            uploadLogo("/api/v1/workshop/branding/logo", owner, "logo.svg", svg.getBytes(StandardCharsets.UTF_8))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("logo-unsafe"));
        }
        call(get("/api/v1/branding"), owner).andExpect(jsonPath("$.logoUrl").isEmpty());
    }

    @Test
    void should_refuse_oversize_and_unsupported_files() throws Exception {
        String owner = signup("Oficina Ficheiros");
        byte[] large = Arrays.copyOf(PNG, 512 * 1024 + 1);

        uploadLogo("/api/v1/workshop/branding/logo", owner, "logo.png", large)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("logo-too-large"));
        uploadLogo("/api/v1/workshop/branding/logo", owner, "logo.png", "GIF89a....".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("logo-unsupported"));
        uploadLogo("/api/v1/workshop/branding/logo", owner, "logo.svg",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"/>".getBytes(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.code").value("logo-unsupported"));
        // The declared type does not matter: an SVG sent as image/png is still checked as SVG.
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/workshop/branding/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"x()\"/>".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.code").value("logo-unsafe"));
    }

    @Test
    void should_let_the_platform_admin_set_any_workshops_theme_and_slug() throws Exception {
        String admin = platformAdmin();
        String id = JsonPath.read(provisionWorkshop("Oficina Do Admin", uniqueEmail("dono"))
                .andReturn().getResponse().getContentAsString(), "$.workshop.id");
        String takenSlug = JsonPath.read(provisionWorkshop("Oficina Ocupada", uniqueEmail("dono"))
                .andReturn().getResponse().getContentAsString(), "$.workshop.slug");
        String slug = "admin-" + id.substring(0, 8);

        call(put("/api/v1/admin/workshops/" + id + "/branding").content(withVersion("""
                {"slug":"%s","displayName":"Do Admin","primaryColor":"#2f9e44","accentColor":"#ffd43b"}"""
                .formatted(slug), versionAt(admin, "/api/v1/admin/workshops/" + id))), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.primaryTextColor").value("#000000"));
        uploadLogo("/api/v1/admin/workshops/" + id + "/branding/logo", admin, "logo.png", PNG)
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/branding/" + slug))
                .andExpect(jsonPath("$.name").value("Do Admin"))
                .andExpect(jsonPath("$.logoUrl", startsWith("/api/v1/branding/" + slug + "/logo")));

        call(put("/api/v1/admin/workshops/" + id + "/branding").content(withVersion("""
                {"slug":"%s"}""".formatted(takenSlug), versionAt(admin, "/api/v1/admin/workshops/" + id))), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("slug-taken"));
        call(put("/api/v1/admin/workshops/" + id + "/branding").content(withVersion("{\"slug\":\"-bad-\"}",
                versionAt(admin, "/api/v1/admin/workshops/" + id))), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").exists());
    }

    private ResultActions uploadLogo(String url, String token, String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, url)
                .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                .header("Authorization", "Bearer " + token));
    }
}
