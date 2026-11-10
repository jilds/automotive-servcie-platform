package com.asp.platform.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.billing.infrastructure.SandboxScenario;
import com.asp.platform.workshop.FloorTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Delivered work orders to invoice, requests to the billing API, the sandbox scripted faults and signed webhook calls.
/// A delivered order from [#delivered] has a labour line (40,00 EUR, 23 %) and 2 parts (12,50 EUR each, 10 % off, 23 %):
/// net 6250, VAT 920 + 518 = 1438, total 7688 cents.
public abstract class BillingTestSupport extends FloorTestSupport {

    protected static final long TOTAL = 7688;
    protected static final String WEBHOOK_SECRET = "test-webhook-secret-0123456789abcdef";

    @Autowired
    protected SandboxScenario scenario;

    @BeforeEach
    void resetSandbox() {
        scenario.reset();
    }

    /// A DELIVERED work order of 7688 cents.
    protected String delivered(Shop g) throws Exception {
        String wo = quotableOrder(g, true);
        deliver(g, wo);
        return wo;
    }

    /// Moves a work order in DIAGNOSTICS to DELIVERED.
    protected void deliver(Shop g, String wo) throws Exception {
        moveOk(g.owner(), wo, "IN_PROGRESS");
        moveOk(g.owner(), wo, "READY");
        moveOk(g.owner(), wo, "DELIVERED");
    }

    protected static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    protected ResultActions issue(String token, String wo, String requestId) throws Exception {
        return issueWith(token, wo, "{\"requestId\":\"" + requestId + "\"}");
    }

    protected ResultActions issueWith(String token, String wo, String body) throws Exception {
        return call(post("/api/v1/work-orders/" + wo + "/invoices").content(body), token);
    }

    /// Issues the invoice of the order as the owner and returns its id.
    protected String issued(Shop g, String wo) throws Exception {
        return idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isCreated()));
    }

    /// A delivered order with an ISSUED invoice; returns the invoice id.
    protected String issuedInvoice(Shop g) throws Exception {
        return issued(g, delivered(g));
    }

    protected String invoiceJson(Shop g, String id) throws Exception {
        return call(get("/api/v1/invoices/" + id), g.owner()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    protected long invoiceVersion(Shop g, String id) throws Exception {
        return ((Number) JsonPath.read(invoiceJson(g, id), "$.version")).longValue();
    }

    protected ResultActions pay(Shop g, String invoiceId, String body) throws Exception {
        return call(post("/api/v1/invoices/" + invoiceId + "/payments").content(body), g.owner());
    }

    protected String cash(Shop g, String invoiceId, long cents) throws Exception {
        return idOf(pay(g, invoiceId, "{\"method\":\"CASH\",\"amountCents\":" + cents + "}")
                .andExpect(status().isCreated()));
    }

    /// A PENDING Multibanco reference for `cents` (the rest of the invoice when 0); returns the payment id.
    protected String multibanco(Shop g, String invoiceId, long cents) throws Exception {
        String amount = cents == 0 ? "" : ",\"amountCents\":" + cents;
        return idOf(pay(g, invoiceId, "{\"method\":\"MULTIBANCO\"" + amount + "}").andExpect(status().isCreated()));
    }

    protected String mbway(Shop g, String invoiceId, String phone) throws Exception {
        return idOf(pay(g, invoiceId, "{\"method\":\"MBWAY\",\"phone\":\"" + phone + "\"}")
                .andExpect(status().isCreated()));
    }

    protected String paymentJson(Shop g, String paymentId) throws Exception {
        return call(get("/api/v1/payments/" + paymentId), g.owner()).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    protected String paymentStatus(Shop g, String paymentId) throws Exception {
        return JsonPath.read(paymentJson(g, paymentId), "$.status");
    }

    protected long paymentVersion(Shop g, String paymentId) throws Exception {
        return ((Number) JsonPath.read(paymentJson(g, paymentId), "$.version")).longValue();
    }

    /// The sandbox provider id for a payment of ours.
    protected static String providerPaymentId(String paymentId) {
        return "sbx-" + paymentId;
    }

    protected String webhookPath(Shop g, String provider) {
        return "/api/v1/public/" + g.slug() + "/billing/webhooks/" + provider;
    }

    protected static String webhookBody(String eventId, String providerPaymentId, String status, long amountCents) {
        return "{\"eventId\":\"%s\",\"paymentId\":\"%s\",\"status\":\"%s\",\"amountCents\":%d}"
                .formatted(eventId, providerPaymentId, status, amountCents);
    }

    /// The `X-ASP-Signature` header value for the body, signed at `epochSeconds`.
    protected static String signature(String body, long epochSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hex = HexFormat.of()
                    .formatHex(mac.doFinal((epochSeconds + "." + body).getBytes(StandardCharsets.UTF_8)));
            return "t=" + epochSeconds + ",v1=" + hex;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String signature(String body) {
        return signature(body, Instant.now().getEpochSecond());
    }

    /// Posts a webhook call from a fresh client address with this signature header (none when null).
    protected ResultActions webhook(Shop g, String provider, String body, String signatureHeader) throws Exception {
        var request = post(webhookPath(g, provider)).content(body);
        if (signatureHeader != null) {
            request.header("X-ASP-Signature", signatureHeader);
        }
        return publicCall(request);
    }

    /// A correctly signed call.
    protected ResultActions signedWebhook(Shop g, String body) throws Exception {
        return webhook(g, "sandbox", body, signature(body));
    }

    protected long count(Shop g, String sql) {
        return asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement(sql); ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }
}
