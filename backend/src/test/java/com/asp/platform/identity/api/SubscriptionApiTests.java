package com.asp.platform.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// A suspended or cancelled workshop is locked out at once: no login, and tokens issued before stop working on
/// their next request (the cached subscription status is evicted on change).
class SubscriptionApiTests extends ApiTestSupport {

    @Test
    void should_lock_out_a_suspended_workshop_including_tokens_issued_before() throws Exception {
        String ownerEmail = uniqueEmail("dono");
        String body = provisionWorkshop("Oficina Suspensa", ownerEmail).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.workshop.id");
        String owner = activate(JsonPath.read(body, "$.activationPath"), "Dono");
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String mechanic = login(mechanicEmail, PASSWORD);
        // Both tokens are used first, so their access and the workshop's status are cached.
        call(get("/api/v1/customers"), owner).andExpect(status().isOk());
        call(get("/api/v1/work-orders/board"), mechanic).andExpect(status().isOk());

        changeStatus(id, "SUSPENDED");

        call(get("/api/v1/customers"), owner)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
                .andExpect(jsonPath("$.code").value("workshop-suspended"))
                .andExpect(jsonPath("$.type").value("/problems/workshop-suspended"));
        call(get("/api/v1/work-orders/board"), mechanic)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("workshop-suspended"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(ownerEmail, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workshop-suspended"));
        // A wrong password still only says "invalid credentials".
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(ownerEmail)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid-credentials"));

        changeStatus(id, "ACTIVE");

        call(get("/api/v1/customers"), owner).andExpect(status().isOk());
        login(mechanicEmail, PASSWORD);
    }

    @Test
    void should_lock_out_a_cancelled_workshop_and_its_pending_activation_links() throws Exception {
        String body = provisionWorkshop("Oficina Cancelada", uniqueEmail("dono"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.workshop.id");

        changeStatus(id, "CANCELLED");

        mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"name\":\"Dono\",\"password\":\"%s\"}"
                        .formatted(tokenOf(JsonPath.read(body, "$.activationPath")), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workshop-suspended"));
        call(get("/api/v1/admin/workshops/" + id), platformAdmin()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void should_keep_other_workshops_running_when_one_is_suspended() throws Exception {
        String other = signup("Oficina Vizinha");
        String body = provisionWorkshop("Oficina Parada", uniqueEmail("dono"))
                .andReturn().getResponse().getContentAsString();

        changeStatus(JsonPath.read(body, "$.workshop.id"), "SUSPENDED");

        call(get("/api/v1/customers"), other).andExpect(status().isOk());
    }

    private void changeStatus(String id, String status) throws Exception {
        call(put("/api/v1/admin/workshops/" + id + "/subscription").content(withVersion("""
                {"plan":"PRO","status":"%s"}""".formatted(status), versionAt(platformAdmin(), "/api/v1/admin/workshops/" + id))), platformAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }
}
