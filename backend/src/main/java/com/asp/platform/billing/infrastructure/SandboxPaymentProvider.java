package com.asp.platform.billing.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.asp.platform.billing.domain.InvalidWebhookBodyException;
import com.asp.platform.billing.domain.InvalidWebhookSignatureException;
import com.asp.platform.billing.domain.PaymentProvider;
import com.asp.platform.billing.domain.ProviderPaymentState;
import com.asp.platform.billing.domain.ProviderRejectedException;
import com.asp.platform.billing.domain.ProviderUnavailableException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// The payment provider used until a real one is chosen: fake Multibanco references (entity 99999) and MB WAY requests
/// that nothing really pays, and a webhook signed with `app.billing.payments.webhook-secret`:
/// header `X-ASP-Signature: t=<epoch seconds>,v1=<hex HMAC-SHA256 of "t.body">`, at most 5 minutes old. Faults are
/// scripted through [SandboxScenario]. The provider payment id is "sbx-" plus our payment id.
@Component
@ConditionalOnProperty(name = "app.billing.payments.provider", havingValue = "sandbox", matchIfMissing = true)
class SandboxPaymentProvider implements PaymentProvider {

    static final String ID = "sandbox";
    static final String SIGNATURE_HEADER = "x-asp-signature";
    private static final String ENTITY = "99999";
    private static final long TOLERANCE_SECONDS = 300;

    private final SandboxScenario scenario;
    private final Clock clock;
    private final JsonMapper json;
    private final String secret;

    SandboxPaymentProvider(SandboxScenario scenario, Clock clock, JsonMapper json,
            com.asp.platform.billing.application.BillingProperties properties) {
        this.scenario = scenario;
        this.clock = clock;
        this.json = json;
        this.secret = properties.payments().webhookSecret();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean sandbox() {
        return true;
    }

    @Override
    public Reference createMultibanco(MultibancoRequest request) {
        fault();
        long digits = Math.floorMod(request.paymentId().getLeastSignificantBits(), 1_000_000_000L);
        return new Reference(providerId(request.paymentId()), ENTITY, "%09d".formatted(digits));
    }

    @Override
    public Reference createMbWay(MbWayRequest request) {
        fault();
        return new Reference(providerId(request.paymentId()), null, null);
    }

    @Override
    public void cancel(String providerPaymentId) {
        // Nothing waits at the sandbox.
    }

    @Override
    public ProviderPaymentState status(String providerPaymentId) {
        return ProviderPaymentState.PENDING;
    }

    @Override
    public Event verifyWebhook(WebhookCall call) {
        verifySignature(call);
        try {
            JsonNode body = json.readTree(call.body());
            String eventId = text(body, "eventId");
            String paymentId = text(body, "paymentId");
            ProviderPaymentState state = ProviderPaymentState.valueOf(text(body, "status"));
            JsonNode amount = body.get("amountCents");
            if (eventId.length() > 100 || amount == null || !amount.canConvertToLong() || amount.asLong() < 0) {
                throw new InvalidWebhookBodyException();
            }
            return new Event(eventId, paymentId, state, amount.asLong());
        } catch (RuntimeException e) {
            throw e instanceof InvalidWebhookBodyException ? e : new InvalidWebhookBodyException();
        }
    }

    private static String providerId(UUID paymentId) {
        return "sbx-" + paymentId;
    }

    private void fault() {
        SandboxScenario.Fault fault = scenario.nextPayment();
        if (fault == SandboxScenario.Fault.REJECT) {
            throw new ProviderRejectedException("sandbox-rejected", "The sandbox refused the payment (scripted)");
        }
        if (fault != null) {
            throw new ProviderUnavailableException("The sandbox did not answer (scripted)");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new InvalidWebhookBodyException();
        }
        return node.asString();
    }

    /// `t=<seconds>,v1=<hex>` over `t + "." + body`, compared in constant time, and not older (or newer) than 5 minutes.
    private void verifySignature(WebhookCall call) {
        String header = call.headers().get(SIGNATURE_HEADER);
        if (secret.isBlank() || header == null) {
            throw new InvalidWebhookSignatureException();
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String trimmed = part.strip();
            if (trimmed.startsWith("t=")) {
                timestamp = trimmed.substring(2);
            } else if (trimmed.startsWith("v1=")) {
                signature = trimmed.substring(3);
            }
        }
        if (timestamp == null || signature == null) {
            throw new InvalidWebhookSignatureException();
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new InvalidWebhookSignatureException();
        }
        if (Math.abs(clock.instant().getEpochSecond() - seconds) > TOLERANCE_SECONDS) {
            throw new InvalidWebhookSignatureException();
        }
        byte[] expected = hmac(timestamp, call.body());
        if (!MessageDigest.isEqual(expected, signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookSignatureException();
        }
    }

    /// The signature in lower-case hex, as bytes of its text (what the header carries).
    private byte[] hmac(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return HexFormat.of().formatHex(mac.doFinal())
                    .getBytes(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
