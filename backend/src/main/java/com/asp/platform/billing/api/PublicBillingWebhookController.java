package com.asp.platform.billing.api;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.billing.application.PaymentWebhookService;
import com.asp.platform.billing.domain.PaymentProvider;
import com.asp.platform.billing.domain.WebhookTooLargeException;
import com.asp.platform.shared.PublicWorkshopRef;

/// The payment provider's webhook (ADR 0004): anonymous, the workshop is the slug's, and the call counts as real only
/// when the provider adapter verifies its signature. The raw body is read as bytes because the signature covers it,
/// and never more than [#MAX_BODY_BYTES] of it: a larger declared length is refused at once and a stream is cut one
/// byte past the limit, both before any signature work (the proxy limits the body too, see deploy/caddy).
@RestController
@RequestMapping("/api/v1/public/{slug}/billing/webhooks")
class PublicBillingWebhookController {

    /// Largest body read; a provider event is a few hundred bytes.
    static final int MAX_BODY_BYTES = 16 * 1024;

    private final PaymentWebhookService webhooks;

    PublicBillingWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    record WebhookResponse(String result) {
    }

    @PostMapping("/{provider}")
    WebhookResponse receive(PublicWorkshopRef workshop, @PathVariable String provider, HttpServletRequest request)
            throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new WebhookTooLargeException();
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new WebhookTooLargeException();
        }
        Map<String, String> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.putIfAbsent(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        }
        return new WebhookResponse(webhooks.handle(workshop.tenantId(), provider,
                new PaymentProvider.WebhookCall(headers, body)).name());
    }
}
