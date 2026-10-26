package com.asp.platform.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Demo requests from the landing site (step 2c): the anonymous form, its limits and CORS, and the platform admin's
/// list and status changes.
class LeadApiTests extends ApiTestSupport {

    private static final String FORM = "/api/v1/public-site/leads";

    @Test
    void should_store_a_request_and_let_the_platform_admin_work_through_it() throws Exception {
        String workshop = "Oficina Proposta " + UUID.randomUUID().toString().substring(0, 6);
        submit(randomAddress(), lead(workshop, "\"912345678\"", "null", "")).andExpect(status().isCreated());

        String admin = platformAdmin();
        String body = call(get("/api/v1/admin/leads?status=NEW"), admin).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.workshopName == '%s')].id".formatted(workshop));
        String id = ids.getFirst();
        call(get("/api/v1/admin/leads"), admin)
                .andExpect(jsonPath("$[?(@.id == '%s')].name".formatted(id)).value("Ana Silva"))
                .andExpect(jsonPath("$[?(@.id == '%s')].phone".formatted(id)).value("912345678"))
                .andExpect(jsonPath("$[?(@.id == '%s')].district".formatted(id)).value("Lisboa"))
                .andExpect(jsonPath("$[?(@.id == '%s')].vehicleType".formatted(id)).value("BOTH"))
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(id)).value("NEW"));

        call(put("/api/v1/admin/leads/" + id + "/status").content("{\"status\":\"CONTACTED\",\"version\":0}"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.statusChangedAt").isNotEmpty());
        // Someone else's older read.
        call(put("/api/v1/admin/leads/" + id + "/status").content("{\"status\":\"CLOSED\",\"version\":0}"), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(get("/api/v1/admin/leads?status=NEW"), admin)
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
        call(put("/api/v1/admin/leads/" + UUID.randomUUID() + "/status")
                .content("{\"status\":\"CLOSED\",\"version\":0}"), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("lead-not-found"));
    }

    @Test
    void should_check_the_form_before_storing_anything() throws Exception {
        submit(randomAddress(), lead("Oficina Sem Contacto", "null", "null", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.email").exists());
        submit(randomAddress(), lead("Oficina Sem Consentimento", "\"912345678\"", "null", "")
                .replace("\"privacyAccepted\":true", "\"privacyAccepted\":false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("privacy-consent-required"));
        submit(randomAddress(), lead("Visite https://evil.example", "\"912345678\"", "null", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.workshopName").exists());
        submit(randomAddress(), lead("Oficina Tipo", "\"912345678\"", "null", "").replace("\"BOTH\"", "\"TRUCK\""))
                .andExpect(status().isBadRequest());
        // Names and the district reach the admins' mail (the workshop name its subject): one line, no links.
        submit(randomAddress(), lead("Oficina\\r\\nBcc: todos@example.pt", "\"912345678\"", "null", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.workshopName").exists());
        submit(randomAddress(), lead("Oficina Distrito", "\"912345678\"", "null", "")
                .replace("\"Lisboa\"", "\"www.evil.example\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.district").exists());
        submit(randomAddress(), lead("Oficina <b>Negrito</b>", "\"912345678\"", "null", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.workshopName").exists());
    }

    @Test
    void should_ask_a_bot_without_consent_for_it_like_a_person() throws Exception {
        submit(randomAddress(), lead("Oficina Robo Sem Consentimento", "\"912345678\"", "null", "http://spam.example")
                .replace("\"privacyAccepted\":true", "\"privacyAccepted\":false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("privacy-consent-required"));
    }

    @Test
    void should_answer_a_bot_like_a_person_and_store_nothing() throws Exception {
        String workshop = "Oficina Robo " + UUID.randomUUID().toString().substring(0, 6);

        submit(randomAddress(), lead(workshop, "\"912345678\"", "null", "http://spam.example"))
                .andExpect(status().isCreated());

        call(get("/api/v1/admin/leads"), platformAdmin())
                .andExpect(jsonPath("$[*].workshopName", not(hasItem(workshop))));
    }

    @Test
    void should_keep_the_list_to_platform_admins_and_never_read_a_token_on_the_form() throws Exception {
        String owner = signup("Oficina Curiosa");

        call(get("/api/v1/admin/leads"), owner).andExpect(status().isForbidden());
        call(put("/api/v1/admin/leads/" + UUID.randomUUID() + "/status")
                .content("{\"status\":\"CLOSED\",\"version\":0}"), owner).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/leads")).andExpect(status().isUnauthorized());
        submit(randomAddress(), lead("Oficina Token", "null", "\"ana@example.pt\"", ""),
                "Bearer not-a-valid-token").andExpect(status().isCreated());
    }

    @Test
    void should_open_only_the_form_on_the_landing_site_api() throws Exception {
        mvc.perform(get(FORM)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/public-site/other").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_limit_requests_per_address() throws Exception {
        String address = randomAddress();
        for (int i = 0; i < 3; i++) {
            submit(address, lead("Oficina Limite " + i, "\"912345678\"", "null", "")).andExpect(status().isCreated());
        }

        submit(address, lead("Oficina Limite 4", "\"912345678\"", "null", ""))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("too-many-requests"));
        // Another address still gets through.
        submit(randomAddress(), lead("Oficina Outra", "\"912345678\"", "null", "")).andExpect(status().isCreated());
    }

    @Test
    void should_let_the_landing_site_read_retry_after_on_a_refused_request() throws Exception {
        String address = randomAddress();
        for (int i = 0; i < 3; i++) {
            submit(address, lead("Oficina Espera " + i, "\"912345678\"", "null", ""), null, "https://asp.test")
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            containsString(HttpHeaders.RETRY_AFTER)));
        }

        submit(address, lead("Oficina Espera 4", "\"912345678\"", "null", ""), null, "https://asp.test")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://asp.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(HttpHeaders.RETRY_AFTER)));
    }

    @Test
    void should_not_count_a_browser_s_preflights() throws Exception {
        String address = randomAddress();
        for (int i = 0; i < 5; i++) {
            mvc.perform(preflight("https://asp.test", address)).andExpect(status().isOk());
        }

        for (int i = 0; i < 3; i++) {
            submit(address, lead("Oficina Preflight " + i, "\"912345678\"", "null", ""), null, "https://asp.test")
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void should_let_only_the_landing_site_post_from_the_browser() throws Exception {
        mvc.perform(preflight("https://asp.test"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://asp.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
        mvc.perform(preflight("https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        submit(randomAddress(), lead("Oficina Site", "\"912345678\"", "null", ""), null, "https://asp.test")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://asp.test"));

        // A post from another site, without asking first, is refused and stores nothing.
        String workshop = "Oficina Estranha " + UUID.randomUUID().toString().substring(0, 6);
        submit(randomAddress(), lead(workshop, "\"912345678\"", "null", ""), null, "https://evil.example")
                .andExpect(status().isForbidden());
        call(get("/api/v1/admin/leads"), platformAdmin())
                .andExpect(jsonPath("$[*].workshopName", not(hasItem(workshop))));
    }

    private static MockHttpServletRequestBuilder preflight(String origin) {
        return preflight(origin, randomAddress());
    }

    private static MockHttpServletRequestBuilder preflight(String origin, String address) {
        return options(FORM).header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                });
    }

    private ResultActions submit(String address, String body) throws Exception {
        return submit(address, body, null, null);
    }

    private ResultActions submit(String address, String body, String authorization) throws Exception {
        return submit(address, body, authorization, null);
    }

    private ResultActions submit(String address, String body, String authorization, String origin) throws Exception {
        MockHttpServletRequestBuilder request = post(FORM).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                });
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        return mvc.perform(request);
    }

    /// @param phone and email as JSON literals (`"…"` or `null`)
    private static String lead(String workshop, String phone, String email, String website) {
        return """
                {"name":"Ana Silva","workshopName":"%s","phone":%s,"email":%s,"district":"Lisboa",
                 "vehicleType":"BOTH","message":"Duas oficinas em Lisboa","privacyAccepted":true,
                 "marketingConsent":false,"website":"%s"}""".formatted(workshop, phone, email, website);
    }

    private static String randomAddress() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10.%d.%d.%d".formatted(random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }
}
