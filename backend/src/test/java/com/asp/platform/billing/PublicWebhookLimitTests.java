package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/// Webhook calls have their own per-address lookup budget (before the slug is looked up), so a flood of unknown slugs
/// to the webhook path is limited too, while a provider is not throttled by the budget of the public pages.
class PublicWebhookLimitTests extends BillingTestSupport {

    private int postTo(String address, String slug) throws Exception {
        return mvc.perform(post("/api/v1/public/" + slug + "/billing/webhooks/sandbox").contentType("application/json")
                .content("{}").with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                })).andReturn().getResponse().getStatus();
    }

    @Test
    void should_limit_post_calls_to_unknown_slugs_by_the_webhook_lookup_budget() throws Exception {
        String address = "10.66.0.1";
        for (int i = 0; i < 120; i++) {
            assertThat(postTo(address, "nope-" + UUID.randomUUID())).as("call %d", i + 1).isEqualTo(404);
        }

        assertThat(postTo(address, "nope-" + UUID.randomUUID())).isEqualTo(429);
    }

    @Test
    void should_count_other_methods_on_the_webhook_path_against_the_normal_lookup_budget() throws Exception {
        String address = "10.66.0.2";
        for (int i = 0; i < 40; i++) {
            int status = mvc.perform(get("/api/v1/public/nope-" + UUID.randomUUID() + "/billing/webhooks/sandbox")
                    .with(r -> {
                        r.setRemoteAddr(address);
                        return r;
                    })).andReturn().getResponse().getStatus();
            assertThat(status).as("call %d", i + 1).isEqualTo(404);
        }

        int refused = mvc.perform(get("/api/v1/public/nope-" + UUID.randomUUID() + "/billing/webhooks/sandbox")
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                })).andReturn().getResponse().getStatus();
        assertThat(refused).isEqualTo(429);
    }
}
