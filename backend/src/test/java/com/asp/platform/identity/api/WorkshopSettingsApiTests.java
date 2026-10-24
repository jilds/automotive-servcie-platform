package com.asp.platform.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;

class WorkshopSettingsApiTests extends ApiTestSupport {

    @Test
    void should_start_with_portuguese_defaults_and_save_changes() throws Exception {
        String owner = signup("Oficina Definições");

        call(get("/api/v1/workshop/settings"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Oficina Definições"))
                .andExpect(jsonPath("$.country").value("PT"))
                .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.defaultVatRate").value(23.00))
                .andExpect(jsonPath("$.labourRateCents").value(0));

        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina Nova","nif":"509999999","country":"es","timezone":"Atlantic/Canary",
                 "defaultVatRate":7,"labourRateCents":4500}""", versionAt(owner, "/api/v1/workshop/settings"))), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Oficina Nova"))
                .andExpect(jsonPath("$.nif").value("509999999"))
                .andExpect(jsonPath("$.country").value("ES"))
                .andExpect(jsonPath("$.timezone").value("Atlantic/Canary"))
                .andExpect(jsonPath("$.defaultVatRate").value(7.00))
                .andExpect(jsonPath("$.labourRateCents").value(4500));

        call(get("/api/v1/workshop/settings"), owner)
                .andExpect(jsonPath("$.name").value("Oficina Nova"))
                .andExpect(jsonPath("$.labourRateCents").value(4500));
    }

    @Test
    void should_return_field_errors_for_invalid_settings() throws Exception {
        String owner = signup("Oficina Inválida");

        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":" ","nif":"12","country":"XX","timezone":"Europe/Atlantis",
                 "defaultVatRate":100.5,"labourRateCents":-1}""",
                 versionAt(owner, "/api/v1/workshop/settings"))), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.nif").exists())
                .andExpect(jsonPath("$.errors.country").exists())
                .andExpect(jsonPath("$.errors.timezone").exists())
                .andExpect(jsonPath("$.errors.defaultVatRate").exists())
                .andExpect(jsonPath("$.errors.labourRateCents").exists());

        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":6.125,
                 "labourRateCents":0}""", versionAt(owner, "/api/v1/workshop/settings"))), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.defaultVatRate").exists());

        call(put("/api/v1/workshop/settings").content(withVersion("{}",
                versionAt(owner, "/api/v1/workshop/settings"))), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.defaultVatRate").exists())
                .andExpect(jsonPath("$.errors.labourRateCents").exists());
    }

    @Test
    void should_let_the_front_desk_read_but_not_change_settings_and_keep_mechanics_out() throws Exception {
        String owner = signup("Oficina Balcão");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String desk = login(deskEmail, PASSWORD);

        call(get("/api/v1/workshop/settings"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultVatRate").value(23.00));
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"X","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,"labourRateCents":0}""",
                versionAt(desk, "/api/v1/workshop/settings"))),
                desk).andExpect(status().isForbidden());
        String mechanic = login(mechanicEmail, PASSWORD);
        call(get("/api/v1/workshop/settings"), mechanic)
                .andExpect(status().isForbidden());
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"X","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,"labourRateCents":0}""",
                versionAt(mechanic, "/api/v1/workshop/settings"))),
                mechanic).andExpect(status().isForbidden());
    }

    @Test
    void should_only_ever_change_the_callers_own_workshop() throws Exception {
        String a = signup("Oficina Própria A");
        String b = signup("Oficina Própria B");

        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"B renamed","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":13,
                 "labourRateCents":3000}""", versionAt(b, "/api/v1/workshop/settings"))), b).andExpect(status().isOk());

        call(get("/api/v1/workshop/settings"), a)
                .andExpect(jsonPath("$.name").value("Oficina Própria A"))
                .andExpect(jsonPath("$.defaultVatRate").value(23.00));
    }
}
