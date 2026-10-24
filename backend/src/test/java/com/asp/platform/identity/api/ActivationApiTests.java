package com.asp.platform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.ApiTestSupport;
import com.asp.platform.identity.domain.ActivationToken;
import com.jayway.jsonpath.JsonPath;

/// Activation links: random, stored only as a SHA-256 hash, single use, valid for 72 hours.
class ActivationApiTests extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void should_activate_once_and_sign_in_the_owner() throws Exception {
        String ownerEmail = uniqueEmail("dono");
        String path = activationPath(provisionWorkshop("Oficina Ativa", ownerEmail));
        String token = tokenOf(path);
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");

        mvc.perform(get("/api/v1/auth/activation").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ownerEmail.toLowerCase()))
                .andExpect(jsonPath("$.workshopName").value("Oficina Ativa"))
                .andExpect(jsonPath("$.expiresAt").exists());

        activateWith(token, "Dona Ana", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.name").value("Dona Ana"))
                .andExpect(jsonPath("$.user.pending").value(false));
        login(ownerEmail, PASSWORD);

        // Single use: the same link never works again.
        activateWith(token, "Intruso", "another-password-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("activation-invalid"));
        mvc.perform(get("/api/v1/auth/activation").param("token", token))
                .andExpect(jsonPath("$.code").value("activation-invalid"));
        login(ownerEmail, PASSWORD);
    }

    @Test
    void should_store_only_the_hash_of_the_token() throws Exception {
        String token = tokenOf(activationPath(provisionWorkshop("Oficina Hash", uniqueEmail("dono"))));

        assertThat(jdbc.queryForObject("select count(*) from activation_token where token_hash = ?", Long.class,
                ActivationToken.hash(token))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from activation_token where token_hash = ?", Long.class,
                token)).isZero();
        assertThat(jdbc.queryForObject("""
                select extract(epoch from expires_at - created_at) / 3600 from activation_token where token_hash = ?""",
                Double.class, ActivationToken.hash(token))).isEqualTo(72.0);
    }

    @Test
    void should_refuse_an_expired_link() throws Exception {
        String token = tokenOf(activationPath(provisionWorkshop("Oficina Expirada", uniqueEmail("dono"))));
        jdbc.update("""
                update activation_token set created_at = now() - interval '73 hours',
                       expires_at = now() - interval '1 hour' where token_hash = ?""", ActivationToken.hash(token));

        activateWith(token, "Dono", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("activation-expired"));
        mvc.perform(get("/api/v1/auth/activation").param("token", token))
                .andExpect(jsonPath("$.code").value("activation-expired"));
    }

    @Test
    void should_refuse_unknown_links_and_short_passwords() throws Exception {
        activateWith("not-a-real-token", "Dono", PASSWORD)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("activation-invalid"));

        String token = tokenOf(activationPath(provisionWorkshop("Oficina Curta", uniqueEmail("dono"))));
        activateWith(token, "Dono", "short-pass")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.password").exists());
        // The failed attempt did not use the link up.
        activateWith(token, "Dono", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void should_let_the_platform_admin_reissue_the_owner_link_and_retire_the_old_one() throws Exception {
        String body = provisionWorkshop("Oficina Reenvio", uniqueEmail("dono")).andReturn().getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.workshop.id");
        String oldToken = tokenOf(JsonPath.read(body, "$.activationPath"));

        String newPath = JsonPath.read(call(post("/api/v1/admin/workshops/" + id + "/owner-activation-link"),
                platformAdmin()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.activationPath");

        activateWith(oldToken, "Dono", PASSWORD).andExpect(jsonPath("$.code").value("activation-invalid"));
        activateWith(tokenOf(newPath), "Dono", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void should_let_the_owner_reissue_a_link_only_for_pending_members_of_their_workshop() throws Exception {
        String owner = signup("Oficina Pendentes");
        String other = signup("Oficina Alheia");
        String pendingId = idOf(call(post("/api/v1/users").content("""
                {"name":"Rui","email":"%s","role":"FRONT_DESK"}""".formatted(uniqueEmail("desk"))), owner));
        String activeId = invite(owner, "MECHANIC", uniqueEmail("mec"));

        String path = JsonPath.read(call(post("/api/v1/users/" + pendingId + "/activation-link"), owner)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.activationPath");
        activateWith(tokenOf(path), "Rui", PASSWORD).andExpect(status().isOk());

        call(post("/api/v1/users/" + activeId + "/activation-link"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already-activated"));
        call(post("/api/v1/users/" + pendingId + "/activation-link"), other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user-not-found"));
    }

    @Test
    void should_not_activate_a_deactivated_member() throws Exception {
        String owner = signup("Oficina Desativa");
        String body = call(post("/api/v1/users").content("""
                {"name":"Rui","email":"%s","role":"MECHANIC"}""".formatted(uniqueEmail("mec"))), owner)
                .andReturn().getResponse().getContentAsString();
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/users/" + JsonPath.read(body, "$.id"))
                .content("{\"role\":\"MECHANIC\",\"active\":false,\"version\":0}"), owner).andExpect(status().isOk());

        activateWith(tokenOf(JsonPath.read(body, "$.activationPath")), "Rui", PASSWORD)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("activation-invalid"));
    }

    private static String activationPath(ResultActions provisioned) throws Exception {
        return JsonPath.read(provisioned.andReturn().getResponse().getContentAsString(), "$.activationPath");
    }

    private ResultActions activateWith(String token, String name, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"name\":\"%s\",\"password\":\"%s\"}".formatted(token, name, password)));
    }
}
