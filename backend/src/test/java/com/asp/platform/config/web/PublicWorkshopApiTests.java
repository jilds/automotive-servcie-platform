package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.asp.platform.ApiTestSupport;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.TenantScope;
import com.jayway.jsonpath.JsonPath;

/// ADR 0004: `/api/v1/public/{slug}/...` is served for the workshop of the slug, anonymously, under row-level
/// security, with rate limits. [PublicProbeController] (test-only) reports what a request sees.
///
/// Each test sends its public requests from its own client address, so the per-address lookup budget
/// (`app.public.rate-limit.lookups`, 40 a minute in the test configuration) is never shared between tests.
class PublicWorkshopApiTests extends ApiTestSupport {

    @Autowired
    WorkshopDirectory directory;

    private String clientAddress;

    @BeforeEach
    void newClientAddress() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        clientAddress = "10.%d.%d.%d".formatted(random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }

    @Test
    void should_serve_each_slug_for_its_own_workshop_only() throws Exception {
        Workshop a = workshop("Oficina Publica A");
        Workshop b = workshop("Oficina Publica B");
        createCustomer(a.owner(), "Cliente A1");
        createCustomer(a.owner(), "Cliente A2");
        createCustomer(b.owner(), "Cliente B1");
        long rateB = 5_250;
        changeLabourRate(b, rateB);

        probe(a.slug())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(a.id()))
                .andExpect(jsonPath("$.scope").value(a.id()))
                .andExpect(jsonPath("$.connectionTenant").value(a.id()))
                .andExpect(jsonPath("$.customers").value(2))
                .andExpect(jsonPath("$.name").value("Oficina Publica A"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.booking").value(false))
                .andExpect(jsonPath("$.shop").value(false));
        probe(b.slug())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(b.id()))
                .andExpect(jsonPath("$.customers").value(1))
                // The catalogue reads the labour rate of the connection's workshop: works under the scope too.
                .andExpect(jsonPath("$.labourRateCents").value(rateB));
    }

    @Test
    void should_answer_workshop_not_found_for_unknown_malformed_suspended_and_cancelled_workshops() throws Exception {
        Workshop suspended = workshop("Oficina Publica Suspensa");
        Workshop cancelled = workshop("Oficina Publica Cancelada");
        changeStatus(suspended, "SUSPENDED");
        changeStatus(cancelled, "CANCELLED");

        for (String slug : new String[] {"no-such-workshop-" + UUID.randomUUID(), "Not_A_Slug", suspended.slug(),
                cancelled.slug()}) {
            probe(slug)
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("workshop-not-found"))
                    .andExpect(jsonPath("$.type").value("/problems/workshop-not-found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Not Found"));
        }

        // The cached miss is evicted when the admin reactivates the workshop.
        changeStatus(suspended, "ACTIVE");
        probe(suspended.slug()).andExpect(status().isOk());
    }

    @Test
    void should_answer_workshop_not_found_on_the_public_root_without_a_slug() throws Exception {
        // Both are on the public security chain: neither may run without a workshop scope.
        for (String path : new String[] {"/api/v1/public", "/api/v1/public/"}) {
            mvc.perform(fromClient(get(path)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("workshop-not-found"));
            mvc.perform(fromClient(post(path)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("workshop-not-found"));
        }
    }

    @Test
    void should_close_the_public_pages_at_once_when_the_admin_suspends_the_workshop() throws Exception {
        Workshop w = workshop("Oficina Publica Cache");
        probe(w.slug()).andExpect(status().isOk());
        probe(w.slug()).andExpect(status().isOk());

        changeStatus(w, "SUSPENDED");

        // No 30-second wait for the cached lookup: the change evicted it.
        probe(w.slug()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("workshop-not-found"));
    }

    @Test
    void should_forget_a_cached_miss_when_a_workshop_takes_the_slug() throws Exception {
        String slug = "oficina-nova-" + UUID.randomUUID().toString().substring(0, 8);
        probe(slug).andExpect(status().isNotFound());

        call(post("/api/v1/admin/workshops").content("""
                {"name":"Oficina Publica Nova","slug":"%s","country":"PT","timezone":"Europe/Lisbon","plan":"PRO",
                 "ownerName":"Dono","ownerEmail":"%s"}""".formatted(slug, uniqueEmail("dono"))), platformAdmin())
                .andExpect(status().isCreated());

        probe(slug).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Oficina Publica Nova"));
    }

    @Test
    void should_never_answer_401_and_ignore_any_bearer_token() throws Exception {
        Workshop a = workshop("Oficina Publica Token A");
        Workshop b = workshop("Oficina Publica Token B");
        createCustomer(b.owner(), "Cliente B");

        // Anonymous, a token that is not a JWT, and a valid token of another workshop: the slug decides.
        probe(a.slug()).andExpect(status().isOk());
        mvc.perform(fromClient(get("/api/v1/public/" + a.slug() + "/probe")
                        .header("Authorization", "Bearer not-a-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(a.id()));
        mvc.perform(fromClient(get("/api/v1/public/" + a.slug() + "/probe")
                        .header("Authorization", "Bearer " + b.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(a.id()))
                .andExpect(jsonPath("$.connectionTenant").value(a.id()))
                .andExpect(jsonPath("$.customers").value(0));
        // Unknown public paths of an open workshop are 404 from Spring MVC, still not 401.
        mvc.perform(fromClient(get("/api/v1/public/" + a.slug() + "/nothing-here")))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_answer_add_on_not_available_until_the_platform_admin_switches_it_on() throws Exception {
        Workshop w = workshop("Oficina Publica Add-on");

        mvc.perform(fromClient(get("/api/v1/public/" + w.slug() + "/probe/booking")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("add-on-not-available"));

        call(put("/api/v1/admin/workshops/" + w.id() + "/add-ons").content(withVersion(
                "{\"booking\":true,\"shop\":false}", adminVersion(w))), platformAdmin())
                .andExpect(status().isOk());

        mvc.perform(fromClient(get("/api/v1/public/" + w.slug() + "/probe/booking"))).andExpect(status().isOk());
        probe(w.slug()).andExpect(jsonPath("$.booking").value(true)).andExpect(jsonPath("$.shop").value(false));
    }

    @Test
    void should_limit_reads_and_writes_per_address_and_workshop() throws Exception {
        Workshop w = workshop("Oficina Publica Limite");
        Workshop other = workshop("Oficina Publica Livre");

        // Test limits: 20 reads a minute, 3 writes an hour (src/test/resources/config/application.yml).
        for (int i = 0; i < 20; i++) {
            probe(w.slug()).andExpect(status().isOk());
        }
        String retryAfter = probe(w.slug())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too-many-requests"))
                .andExpect(jsonPath("$.type").value("/problems/too-many-requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);

        // Writes count separately.
        for (int i = 0; i < 3; i++) {
            mvc.perform(fromClient(post("/api/v1/public/" + w.slug() + "/probe"))).andExpect(status().isOk());
        }
        String writeRetry = mvc.perform(fromClient(post("/api/v1/public/" + w.slug() + "/probe")))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(writeRetry)).isBetween(61L, 3600L);

        // Another workshop from the same address has its own counters.
        probe(other.slug()).andExpect(status().isOk());
    }

    @Test
    void should_limit_lookups_per_address_before_the_slug_is_looked_up() throws Exception {
        Workshop open = workshop("Oficina Publica Inundada");

        // Test budget: 40 public requests a minute per address, across all slugs, known or not.
        for (int i = 0; i < 40; i++) {
            probe("no-such-workshop-" + i).andExpect(status().isNotFound());
        }
        String retryAfter = probe("no-such-workshop-40")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too-many-requests"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
        // The budget is the address's, so an open workshop is refused too; another address is not.
        probe(open.slug()).andExpect(status().isTooManyRequests());
        newClientAddress();
        probe(open.slug()).andExpect(status().isOk());
    }

    @Test
    void should_leave_no_scope_behind_and_keep_the_signed_in_api_as_before() throws Exception {
        Workshop a = workshop("Oficina Publica Fuga A");
        Workshop b = workshop("Oficina Publica Fuga B");
        createCustomer(b.owner(), "Cliente B");

        probe(a.slug()).andExpect(status().isOk());

        assertThat(TenantScope.current()).isEmpty();
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        call(get("/api/v1/customers"), b.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/customers"), a.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_list_open_workshops_for_jobs() throws Exception {
        Workshop open = workshop("Oficina Publica Aberta");
        Workshop closed = workshop("Oficina Publica Fechada");
        changeStatus(closed, "SUSPENDED");

        assertThat(directory.openWorkshops())
                .contains(UUID.fromString(open.id()))
                .doesNotContain(UUID.fromString(closed.id()));
        assertThat(directory.publicWorkshop(closed.slug())).isEmpty();
        assertThat(directory.publicWorkshop(open.slug())).hasValueSatisfying(
                w -> assertThat(w.tenantId()).isEqualTo(UUID.fromString(open.id())));
    }

    private ResultActions probe(String slug) throws Exception {
        return mvc.perform(fromClient(get("/api/v1/public/" + slug + "/probe")));
    }

    private MockHttpServletRequestBuilder fromClient(MockHttpServletRequestBuilder request) {
        String address = clientAddress;
        return request.with(r -> {
            r.setRemoteAddr(address);
            return r;
        });
    }

    private Workshop workshop(String name) throws Exception {
        String body = provisionWorkshop(name, uniqueEmail("dono")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String owner = activate(JsonPath.read(body, "$.activationPath"), "Dono");
        return new Workshop(JsonPath.read(body, "$.workshop.id"), JsonPath.read(body, "$.workshop.slug"), owner);
    }

    private long adminVersion(Workshop w) throws Exception {
        return versionAt(platformAdmin(), "/api/v1/admin/workshops/" + w.id());
    }

    private void changeStatus(Workshop w, String status) throws Exception {
        call(put("/api/v1/admin/workshops/" + w.id() + "/subscription").content(withVersion(
                "{\"plan\":\"PRO\",\"status\":\"%s\"}".formatted(status), adminVersion(w))), platformAdmin())
                .andExpect(status().isOk());
    }

    private void changeLabourRate(Workshop w, long cents) throws Exception {
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina Publica B","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                 "labourRateCents":%d}""".formatted(cents), versionAt(w.owner(), "/api/v1/workshop/settings"))),
                w.owner())
                .andExpect(status().isOk());
    }

    private record Workshop(String id, String slug, String owner) {
    }
}
