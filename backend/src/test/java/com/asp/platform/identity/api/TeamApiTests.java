package com.asp.platform.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

class TeamApiTests extends ApiTestSupport {

    @Test
    void should_change_a_role_and_stop_a_deactivated_user_from_logging_in() throws Exception {
        String owner = signup("Oficina Equipa 2");
        String email = uniqueEmail("desk");
        String userId = invite(owner, "FRONT_DESK", email);

        call(get("/api/v1/users"), owner)
                .andExpect(jsonPath("$[?(@.id == '%s')].active".formatted(userId)).value(true));

        call(put("/api/v1/users/" + userId).content("{\"role\":\"MECHANIC\",\"active\":true}"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.role").value("MECHANIC"))
                .andExpect(jsonPath("$.active").value(true));
        login(email, "password123");

        call(put("/api/v1/users/" + userId).content("{\"role\":\"MECHANIC\",\"active\":false}"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid-credentials"));

        // Reactivated, they can log in again.
        call(put("/api/v1/users/" + userId).content("{\"role\":\"MECHANIC\",\"active\":true}"), owner)
                .andExpect(status().isOk());
        login(email, "password123");
    }

    @Test
    void should_not_let_owners_change_their_own_role_or_deactivate_themselves() throws Exception {
        String owner = signup("Oficina Eu");
        String ownerId = ownId(owner);

        call(put("/api/v1/users/" + ownerId).content("{\"role\":\"FRONT_DESK\",\"active\":true}"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/own-account-change"))
                .andExpect(jsonPath("$.code").value("own-account-change"));
        call(put("/api/v1/users/" + ownerId).content("{\"role\":\"OWNER\",\"active\":false}"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("own-account-change"));
        call(put("/api/v1/users/" + ownerId).content("{\"role\":\"OWNER\",\"active\":true}"), owner)
                .andExpect(status().isOk());
    }

    /// The "last active owner" rule itself is covered by `TeamTests`: through the API an owner can only hit it
    /// via their own account (`own-account-change`) or a race, because a deactivated owner's token stops working.
    @Test
    void should_lock_out_a_deactivated_owner_and_keep_the_remaining_owner() throws Exception {
        String first = signup("Oficina Donos");
        String firstId = ownId(first);
        String secondEmail = uniqueEmail("owner2");
        String secondId = invite(first, "OWNER", secondEmail);
        String second = login(secondEmail, "password123");

        // The second owner deactivates the first; one active owner is left.
        call(put("/api/v1/users/" + firstId).content("{\"role\":\"OWNER\",\"active\":false}"), second)
                .andExpect(status().isOk());

        // The first owner's token has not expired, but it no longer works at all.
        call(put("/api/v1/users/" + secondId).content("{\"role\":\"OWNER\",\"active\":false}"), first)
                .andExpect(status().isUnauthorized());
        call(get("/api/v1/users"), second)
                .andExpect(jsonPath("$[?(@.id == '%s')].active".formatted(secondId)).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].role".formatted(secondId)).value("OWNER"));
    }

    @Test
    void should_not_reach_users_of_another_workshop() throws Exception {
        String a = signup("Oficina Pessoas A");
        String b = signup("Oficina Pessoas B");
        String userOfA = invite(a, "MECHANIC", uniqueEmail("mec"));

        call(put("/api/v1/users/" + userOfA).content("{\"role\":\"OWNER\",\"active\":true}"), b)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/user-not-found"))
                .andExpect(jsonPath("$.code").value("user-not-found"));
        call(get("/api/v1/users"), a)
                .andExpect(jsonPath("$[?(@.id == '%s')].role".formatted(userOfA)).value("MECHANIC"));
    }

    @Test
    void should_validate_the_change_and_keep_it_for_owners() throws Exception {
        String owner = signup("Oficina Regras");
        String deskEmail = uniqueEmail("desk");
        String deskId = invite(owner, "FRONT_DESK", deskEmail);

        call(put("/api/v1/users/" + deskId).content("{}"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists())
                .andExpect(jsonPath("$.errors.active").exists());
        call(put("/api/v1/users/" + deskId).content("{\"role\":\"MECHANIC\",\"active\":true}"),
                login(deskEmail, "password123"))
                .andExpect(status().isForbidden());
    }

    private String ownId(String token) throws Exception {
        String users = call(get("/api/v1/users"), token).andReturn().getResponse().getContentAsString();
        return JsonPath.read(users, "$[0].id");
    }
}
