package com.asp.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;
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
///
/// Workshops are registered the way production does it (Release 3, ADR 0003): the platform admin from
/// `src/test/resources/config/application.yml` creates the workshop, and the owner sets [#PASSWORD] through the
/// activation link. Invited team members activate with [#PASSWORD] too.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    /// Password of every owner and team member the helpers create.
    protected static final String PASSWORD = "password-1234";
    protected static final String PLATFORM_ADMIN_EMAIL = "platform-admin@asp.test";
    protected static final String PLATFORM_ADMIN_PASSWORD = "platform-admin-test-password";

    @Autowired
    protected MockMvc mvc;

    /// Registers a new workshop through the platform admin, activates its owner and returns the owner's token.
    protected String signup(String workshop) throws Exception {
        String activationPath = JsonPath.read(provisionWorkshop(workshop, uniqueEmail("owner"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.activationPath");
        return activate(activationPath, "Owner");
    }

    /// `POST /admin/workshops` as the platform admin, with Portuguese defaults and plan PRO.
    protected ResultActions provisionWorkshop(String workshop, String ownerEmail) throws Exception {
        return call(post("/api/v1/admin/workshops").content("""
                {"name":"%s","country":"PT","timezone":"Europe/Lisbon","plan":"PRO",
                 "ownerName":"Owner","ownerEmail":"%s"}""".formatted(workshop, ownerEmail)), platformAdmin());
    }

    /// Opens an activation link with [#PASSWORD] and returns the new access token.
    protected String activate(String activationPath, String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"name\":\"%s\",\"password\":\"%s\"}"
                        .formatted(tokenOf(activationPath), name, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    protected String platformAdmin() throws Exception {
        return login(PLATFORM_ADMIN_EMAIL, PLATFORM_ADMIN_PASSWORD);
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

    /// Invites a team member, activates them with [#PASSWORD] and returns their user id.
    protected String invite(String ownerToken, String role, String email) throws Exception {
        String body = call(post("/api/v1/users").content("""
                {"name":"%s","email":"%s","role":"%s"}"""
                .formatted(role.toLowerCase(Locale.ROOT), email, role)), ownerToken)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        activate(JsonPath.read(body, "$.activationPath"), role.toLowerCase(Locale.ROOT));
        return JsonPath.read(body, "$.id");
    }

    /// The `version` of the record at `path` (optimistic locking), or 0 when the caller cannot read it (another
    /// workshop's record, a role without access): the request under test is then refused before the version matters.
    protected long versionAt(String token, String path) throws Exception {
        var response = call(get(path), token).andReturn().getResponse();
        if (response.getStatus() != 200) {
            return 0;
        }
        return ((Number) JsonPath.read(response.getContentAsString(), "$.version")).longValue();
    }

    /// The `version` of a team member as the owner sees it in `GET /users`, or 0 when not in the list.
    protected long userVersion(String ownerToken, String userId) throws Exception {
        var response = call(get("/api/v1/users"), ownerToken).andReturn().getResponse();
        if (response.getStatus() != 200) {
            return 0;
        }
        List<Number> versions = JsonPath.read(response.getContentAsString(),
                "$[?(@.id == '" + userId + "')].version");
        return versions.isEmpty() ? 0 : versions.getFirst().longValue();
    }

    /// Adds `"version": n` to a JSON object body.
    protected static String withVersion(String json, long version) {
        String body = json.strip();
        String rest = body.substring(1).strip();
        return "{\"version\":" + version + (rest.startsWith("}") ? "" : ",") + rest;
    }

    protected ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
    }

    protected static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /// The token in an activation path such as `/activate?token=abc`.
    protected static String tokenOf(String activationPath) {
        return activationPath.substring(activationPath.indexOf("token=") + "token=".length());
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.pt";
    }
}
