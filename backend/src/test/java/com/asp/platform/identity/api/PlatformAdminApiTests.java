package com.asp.platform.identity.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// The platform admin registers workshops and manages subscriptions, but belongs to no workshop and can never read
/// a workshop's business data.
class PlatformAdminApiTests extends ApiTestSupport {

    @Test
    void should_sign_in_the_platform_admin_through_the_same_login_without_a_workshop() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(PLATFORM_ADMIN_EMAIL,
                        PLATFORM_ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.user.tenantId").isEmpty());
    }

    @Test
    void should_answer_403_to_a_platform_admin_on_every_workshop_api() throws Exception {
        String owner = signup("Oficina Privada");
        String customerId = createCustomer(owner, "Cliente Secreto");
        String admin = platformAdmin();

        call(get("/api/v1/customers"), admin).andExpect(status().isForbidden());
        call(get("/api/v1/customers/" + customerId), admin).andExpect(status().isForbidden());
        call(get("/api/v1/work-orders/board"), admin).andExpect(status().isForbidden());
        call(get("/api/v1/dashboard"), admin).andExpect(status().isForbidden());
        call(get("/api/v1/users"), admin).andExpect(status().isForbidden());
        call(get("/api/v1/workshop/settings"), admin).andExpect(status().isForbidden());
        call(get("/api/v1/branding"), admin).andExpect(status().isForbidden());
        call(post("/api/v1/customers").content("{\"name\":\"X\"}"), admin).andExpect(status().isForbidden());
    }

    @Test
    void should_keep_the_console_for_platform_admins_only() throws Exception {
        String owner = signup("Oficina Curiosa");

        call(get("/api/v1/admin/workshops"), owner).andExpect(status().isForbidden());
        call(post("/api/v1/admin/workshops").content("{}"), owner).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/workshops")).andExpect(status().isUnauthorized());
    }

    @Test
    void should_register_a_workshop_with_its_owner_and_list_it() throws Exception {
        String ownerEmail = uniqueEmail("dono");
        String body = call(post("/api/v1/admin/workshops").content("""
                {"name":"Auto Reparadora Silva","nif":"509999999","country":"pt","timezone":"Europe/Lisbon",
                 "plan":"STARTER","status":"TRIAL","subscriptionUntil":"2026-12-31",
                 "ownerName":"Maria Silva","ownerEmail":"%s"}""".formatted(ownerEmail)), platformAdmin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workshop.name").value("Auto Reparadora Silva"))
                .andExpect(jsonPath("$.workshop.country").value("PT"))
                .andExpect(jsonPath("$.workshop.plan").value("STARTER"))
                .andExpect(jsonPath("$.workshop.status").value("TRIAL"))
                .andExpect(jsonPath("$.workshop.subscriptionUntil").value("2026-12-31"))
                .andExpect(jsonPath("$.workshop.slug", startsWith("auto-reparadora-silva")))
                .andExpect(jsonPath("$.workshop.ownerEmail").value(ownerEmail.toLowerCase()))
                .andExpect(jsonPath("$.workshop.users").value(1))
                .andExpect(jsonPath("$.activationPath", startsWith("/activate?token=")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.workshop.id");

        call(get("/api/v1/admin/workshops"), platformAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].ownerEmail".formatted(id)).value(ownerEmail.toLowerCase()))
                .andExpect(jsonPath("$[?(@.id == '%s')].plan".formatted(id)).value("STARTER"));

        String owner = activate(JsonPath.read(body, "$.activationPath"), "Maria Silva");
        invite(owner, "MECHANIC", uniqueEmail("mec"));
        call(get("/api/v1/admin/workshops/" + id), platformAdmin())
                .andExpect(jsonPath("$.users").value(2))
                .andExpect(jsonPath("$.nif").value("509999999"));
        call(get("/api/v1/workshop/settings"), owner)
                .andExpect(jsonPath("$.name").value("Auto Reparadora Silva"))
                .andExpect(jsonPath("$.defaultVatRate").value(23.00));
    }

    @Test
    void should_give_each_workshop_a_unique_slug() throws Exception {
        String admin = platformAdmin();
        String first = JsonPath.read(provisionWorkshop("Oficina Gémea", uniqueEmail("a"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.workshop.slug");
        String second = JsonPath.read(provisionWorkshop("Oficina Gémea", uniqueEmail("b"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.workshop.slug");
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first).startsWith("oficina-gemea");

        call(post("/api/v1/admin/workshops").content("""
                {"name":"Outra","country":"PT","timezone":"Europe/Lisbon","plan":"PRO","slug":"%s",
                 "ownerName":"X","ownerEmail":"%s"}""".formatted(first, uniqueEmail("c"))), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("slug-taken"));
    }

    @Test
    void should_validate_a_new_workshop() throws Exception {
        String admin = platformAdmin();
        call(post("/api/v1/admin/workshops").content("""
                {"name":"","country":"XX","timezone":"Mars/Base","slug":"Not A Slug","ownerEmail":"nope"}"""), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.country").exists())
                .andExpect(jsonPath("$.errors.timezone").exists())
                .andExpect(jsonPath("$.errors.plan").exists())
                .andExpect(jsonPath("$.errors.slug").exists())
                .andExpect(jsonPath("$.errors.ownerName").exists())
                .andExpect(jsonPath("$.errors.ownerEmail").exists());

        call(post("/api/v1/admin/workshops").content("""
                {"name":"Suspensa","country":"PT","timezone":"Europe/Lisbon","plan":"PRO","status":"SUSPENDED",
                 "ownerName":"X","ownerEmail":"%s"}""".formatted(uniqueEmail("s"))), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"));

        String takenEmail = uniqueEmail("taken");
        provisionWorkshop("Primeira", takenEmail).andExpect(status().isCreated());
        provisionWorkshop("Segunda", takenEmail)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email-taken"));
        provisionWorkshop("Terceira", PLATFORM_ADMIN_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email-taken"));
    }

    @Test
    void should_change_the_plan_and_report_an_unknown_workshop() throws Exception {
        String id = JsonPath.read(provisionWorkshop("Oficina Plano", uniqueEmail("p"))
                .andReturn().getResponse().getContentAsString(), "$.workshop.id");

        call(put("/api/v1/admin/workshops/" + id + "/subscription").content(withVersion("""
                {"plan":"ENTERPRISE","status":"ACTIVE","subscriptionUntil":"2027-06-30"}""",
                versionAt(platformAdmin(), "/api/v1/admin/workshops/" + id))), platformAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("ENTERPRISE"))
                .andExpect(jsonPath("$.subscriptionUntil").value("2027-06-30"));
        call(put("/api/v1/admin/workshops/" + id + "/subscription").content(withVersion("{\"plan\":\"GOLD\"}",
                versionAt(platformAdmin(), "/api/v1/admin/workshops/" + id))), platformAdmin())
                .andExpect(status().isBadRequest());
        call(get("/api/v1/admin/workshops/00000000-0000-0000-0000-000000000000"), platformAdmin())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("workshop-not-found"));
    }
}
