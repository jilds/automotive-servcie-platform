package com.asp.platform.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.asp.platform.ApiTestSupport;

class IdentityApiTests extends ApiTestSupport {

    @Test
    void should_return_401_when_not_logged_in_or_password_wrong() throws Exception {
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.pt\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid-credentials"));
    }

    @Test
    void should_let_the_owner_invite_a_mechanic_who_can_then_log_in() throws Exception {
        String owner = signup("Oficina Equipa");
        String email = uniqueEmail("MEC");
        call(post("/api/v1/users").content("""
                {"name":"Rui","email":"%s","role":"MECHANIC","temporaryPassword":"mechanic123"}""".formatted(email)),
                owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));

        String mechanic = login(email, "mechanic123");
        call(get("/api/v1/users"), mechanic).andExpect(status().isForbidden());
        call(get("/api/v1/users"), owner).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void should_return_field_errors_when_signup_is_invalid() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"workshopName\":\"\",\"ownerName\":\"A\",\"email\":\"not-an-email\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
