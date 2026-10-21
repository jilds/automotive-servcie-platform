package com.asp.platform.identity.api;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;

/// Tokens live for 12 h, but a deactivated or demoted user loses their rights on the very next request.
/// Each test first uses the token, so the user's access is cached, then changes it and expects the change
/// at once: the change evicts the cache entry instead of waiting out its 30 s.
class AccessRevocationApiTests extends ApiTestSupport {

    @Test
    void should_reject_the_old_token_with_401_when_the_user_is_deactivated() throws Exception {
        String owner = signup("Oficina Revoga");
        String email = uniqueEmail("mec");
        String mechanicId = invite(owner, "MECHANIC", email);
        String mechanic = login(email, "password123");
        call(get("/api/v1/work-orders/board"), mechanic).andExpect(status().isOk());

        call(put("/api/v1/users/" + mechanicId).content("{\"role\":\"MECHANIC\",\"active\":false}"), owner)
                .andExpect(status().isOk());

        call(get("/api/v1/work-orders/board"), mechanic)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        allOf(startsWith("Bearer"), containsString("error=\"invalid_token\""))));
    }

    @Test
    void should_accept_the_old_token_again_when_the_user_is_reactivated() throws Exception {
        String owner = signup("Oficina Volta");
        String email = uniqueEmail("desk");
        String deskId = invite(owner, "FRONT_DESK", email);
        String desk = login(email, "password123");

        call(put("/api/v1/users/" + deskId).content("{\"role\":\"FRONT_DESK\",\"active\":false}"), owner)
                .andExpect(status().isOk());
        call(get("/api/v1/customers"), desk).andExpect(status().isUnauthorized());

        call(put("/api/v1/users/" + deskId).content("{\"role\":\"FRONT_DESK\",\"active\":true}"), owner)
                .andExpect(status().isOk());
        call(get("/api/v1/customers"), desk).andExpect(status().isOk());
    }

    @Test
    void should_take_owner_rights_from_the_old_token_when_the_owner_is_demoted() throws Exception {
        String first = signup("Oficina Despromove");
        String secondEmail = uniqueEmail("owner2");
        String secondId = invite(first, "OWNER", secondEmail);
        String second = login(secondEmail, "password123");
        String desk = invite(first, "FRONT_DESK", uniqueEmail("desk"));
        call(get("/api/v1/dashboard"), second).andExpect(status().isOk());

        call(put("/api/v1/users/" + secondId).content("{\"role\":\"MECHANIC\",\"active\":true}"), first)
                .andExpect(status().isOk());

        // The token still says OWNER, but the database role decides.
        call(get("/api/v1/dashboard"), second).andExpect(status().isForbidden());
        call(put("/api/v1/users/" + desk).content("{\"role\":\"OWNER\",\"active\":true}"), second)
                .andExpect(status().isForbidden());
        // Requests their new role allows still work.
        call(get("/api/v1/work-orders/board"), second).andExpect(status().isOk());
    }

    @Test
    void should_grant_new_rights_to_the_old_token_when_a_user_is_promoted() throws Exception {
        String owner = signup("Oficina Promove");
        String email = uniqueEmail("desk");
        String deskId = invite(owner, "FRONT_DESK", email);
        String desk = login(email, "password123");
        call(get("/api/v1/dashboard"), desk).andExpect(status().isForbidden());

        call(put("/api/v1/users/" + deskId).content("{\"role\":\"OWNER\",\"active\":true}"), owner)
                .andExpect(status().isOk());

        call(get("/api/v1/dashboard"), desk).andExpect(status().isOk());
    }

    @Test
    void should_keep_serving_requests_of_active_users() throws Exception {
        String owner = signup("Oficina Normal");

        call(get("/api/v1/dashboard"), owner).andExpect(status().isOk());
        call(get("/api/v1/users"), owner).andExpect(status().isOk());
        call(get("/api/v1/work-orders/board"), owner).andExpect(status().isOk());
    }

    @Test
    void should_reject_a_forged_or_missing_token() throws Exception {
        call(get("/api/v1/work-orders/board"), "not-a-jwt").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/work-orders/board")).andExpect(status().isUnauthorized());
    }
}
