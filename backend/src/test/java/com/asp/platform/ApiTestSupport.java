package com.asp.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/// Full-stack API tests on a real PostgreSQL, with row-level security on. Shares one Spring context.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    /// Signs up a new workshop and returns its owner's access token.
    protected String signup(String workshop) throws Exception {
        String body = """
                {"workshopName":"%s","ownerName":"Owner","email":"%s","password":"password123"}"""
                .formatted(workshop, uniqueEmail("owner"));
        return JsonPath.read(mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");
    }

    protected String login(String email, String password) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    protected String createCustomer(String token, String name) throws Exception {
        return idOf(call(post("/api/v1/customers").content("""
                {"name":"%s","nif":"123456789","phone":"+351912345678"}""".formatted(name)), token)
                .andExpect(status().isCreated()));
    }

    protected String createVehicle(String token, String customerId, String plate) throws Exception {
        return idOf(call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"%s","make":"Renault","model":"Clio","year":2019,"mileage":100000}"""
                .formatted(customerId, plate)), token).andExpect(status().isCreated()));
    }

    /// Invites a team member with password `password123` and returns their user id.
    protected String invite(String ownerToken, String role, String email) throws Exception {
        return idOf(call(post("/api/v1/users").content("""
                {"name":"%s","email":"%s","role":"%s","temporaryPassword":"password123"}"""
                .formatted(role.toLowerCase(java.util.Locale.ROOT), email, role)), ownerToken)
                .andExpect(status().isCreated()));
    }

    protected ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
    }

    protected static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.pt";
    }
}
