package com.asp.platform.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Release 3 rules on the workshop and its team: optimistic locking (`version`, 409 `stale-data`) on settings,
/// branding, subscription and team changes, and the Portuguese NIF check digit on the workshop's NIF.
class IdentityVersionAndNifApiTests extends ApiTestSupport {

    private static final String SETTINGS = """
            {"name":"Oficina","nif":%s,"country":"%s","timezone":"Europe/Lisbon","defaultVatRate":23,
             "labourRateCents":4000,"version":%d}""";

    @Test
    void should_share_one_workshop_version_between_settings_and_branding() throws Exception {
        String owner = signup("Oficina Versao");
        call(get("/api/v1/workshop/settings"), owner).andExpect(jsonPath("$.version").value(0));

        call(put("/api/v1/workshop/settings").content(SETTINGS.formatted("null", "PT", 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // A second owner tab still holds version 0.
        call(put("/api/v1/workshop/settings").content(SETTINGS.formatted("null", "PT", 0)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));

        // Settings and theme live on the same row: the theme form must send the new version too.
        call(put("/api/v1/workshop/branding").content("{\"primaryColor\":\"#0b3d91\",\"version\":0}"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(put("/api/v1/workshop/branding").content("{\"primaryColor\":\"#0b3d91\",\"version\":1}"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#0b3d91"))
                .andExpect(jsonPath("$.version").value(2));
        call(get("/api/v1/branding"), owner).andExpect(jsonPath("$.version").value(2));

        call(put("/api/v1/workshop/branding").content("{\"primaryColor\":\"#0b3d91\"}"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
    }

    @Test
    void should_check_the_nif_of_a_portuguese_workshop_only() throws Exception {
        String owner = signup("Oficina NIF Settings");

        call(put("/api/v1/workshop/settings").content(SETTINGS.formatted("\"123456780\"", "PT", 0)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.nif").exists());
        call(put("/api/v1/workshop/settings").content(SETTINGS.formatted("\"509999999\"", "PT", 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nif").value("509999999"));
        // A Spanish workshop's tax number is not a Portuguese NIF: stored as entered.
        call(put("/api/v1/workshop/settings").content(SETTINGS.formatted("\"123456780\"", "ES", 1)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nif").value("123456780"));
    }

    @Test
    void should_check_the_nif_when_the_platform_admin_registers_a_workshop() throws Exception {
        call(post("/api/v1/admin/workshops").content("""
                {"name":"Oficina NIF Errado","nif":"123456780","country":"PT","timezone":"Europe/Lisbon",
                 "plan":"PRO","ownerName":"Ana","ownerEmail":"%s"}""".formatted(uniqueEmail("nif"))),
                platformAdmin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.nif").exists());
        call(post("/api/v1/admin/workshops").content("""
                {"name":"Oficina NIF Certo","nif":"123456789","country":"PT","timezone":"Europe/Lisbon",
                 "plan":"PRO","ownerName":"Ana","ownerEmail":"%s"}""".formatted(uniqueEmail("nif"))),
                platformAdmin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workshop.nif").value("123456789"))
                .andExpect(jsonPath("$.workshop.version").value(0));
    }

    @Test
    void should_refuse_stale_admin_changes_to_a_workshop() throws Exception {
        String admin = platformAdmin();
        String id = JsonPath.read(provisionWorkshop("Oficina Admin Versao", uniqueEmail("owner"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.workshop.id");

        call(put("/api/v1/admin/workshops/" + id + "/subscription").content("""
                {"plan":"ENTERPRISE","status":"ACTIVE","version":0}"""), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("ENTERPRISE"))
                .andExpect(jsonPath("$.version").value(1));
        call(put("/api/v1/admin/workshops/" + id + "/subscription").content("""
                {"plan":"STARTER","status":"SUSPENDED","version":0}"""), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(put("/api/v1/admin/workshops/" + id + "/branding").content("""
                {"displayName":"Velha","version":0}"""), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(get("/api/v1/admin/workshops/" + id), admin)
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.displayName").isEmpty());
    }

    @Test
    void should_refuse_a_stale_team_change() throws Exception {
        String owner = signup("Oficina Equipa Versao");
        String deskId = invite(owner, "FRONT_DESK", uniqueEmail("desk"));
        long version = userVersion(owner, deskId);

        call(put("/api/v1/users/" + deskId).content("""
                {"role":"MECHANIC","active":true,"version":%d}""".formatted(version)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MECHANIC"))
                .andExpect(jsonPath("$.version").value(version + 1));
        call(put("/api/v1/users/" + deskId).content("""
                {"role":"OWNER","active":true,"version":%d}""".formatted(version)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(get("/api/v1/users"), owner)
                .andExpect(jsonPath("$[?(@.id == '%s')].role".formatted(deskId)).value("MECHANIC"));
    }
}
