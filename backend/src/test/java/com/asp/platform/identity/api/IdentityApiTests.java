package com.asp.platform.identity.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

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
    void should_no_longer_offer_public_signup() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"workshopName\":\"X\",\"ownerName\":\"A\",\"email\":\"a@test.pt\",\"password\":\"password-1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_invite_a_mechanic_with_an_activation_link_instead_of_a_password() throws Exception {
        String owner = signup("Oficina Equipa");
        String email = uniqueEmail("MEC");
        String body = call(post("/api/v1/users").content("""
                {"name":"Rui","email":"%s","role":"MECHANIC"}""".formatted(email)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.pending").value(true))
                .andExpect(jsonPath("$.activationPath", startsWith("/activate?token=")))
                .andExpect(jsonPath("$.activationExpiresAt").exists())
                .andReturn().getResponse().getContentAsString();

        // No password yet: the user cannot log in until they open the link.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
        call(get("/api/v1/users"), owner)
                .andExpect(jsonPath("$[?(@.email == '%s')].pending".formatted(email.toLowerCase())).value(true));

        String mechanic = activate(JsonPath.read(body, "$.activationPath"), "Rui Costa");
        call(get("/api/v1/users"), mechanic).andExpect(status().isForbidden());
        call(get("/api/v1/users"), owner)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email == '%s')].pending".formatted(email.toLowerCase())).value(false))
                .andExpect(jsonPath("$[?(@.email == '%s')].name".formatted(email.toLowerCase())).value("Rui Costa"));
        login(email, PASSWORD);
    }

    @Test
    void should_validate_the_invite() throws Exception {
        String owner = signup("Oficina Convites");
        call(post("/api/v1/users").content("""
                {"name":"","email":"not-an-email","role":null}"""), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    void should_refuse_an_email_that_a_platform_admin_uses() throws Exception {
        String owner = signup("Oficina Emails");
        call(post("/api/v1/users").content("""
                {"name":"X","email":"%s","role":"MECHANIC"}""".formatted(PLATFORM_ADMIN_EMAIL)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email-taken"));
    }

    @Test
    void should_return_the_platform_contact_publicly() throws Exception {
        mvc.perform(get("/api/v1/platform/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.phone").isEmpty());
    }
}
