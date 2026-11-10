package com.asp.platform.billing.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A payment service (ifthenpay, Eupago, SIBS ...) that creates Multibanco references and MB WAY requests and tells us
/// when they are paid.
public interface PaymentProvider {

    String id();

    /// False for the stand-in used when the provider is none: every call that would reach it fails.
    default boolean configured() {
        return true;
    }

    boolean sandbox();

    /// @param paymentId our payment id, the external reference at the provider
    record MultibancoRequest(UUID paymentId, long amountCents, Instant expiresAt) {
    }

    /// @param phone normalised, "+351912345678"
    record MbWayRequest(UUID paymentId, String phone, long amountCents, Instant expiresAt) {

        @Override
        public String toString() {
            return "MbWayRequest[paymentId=" + paymentId + ", amountCents=" + amountCents + "]";
        }
    }

    /// @param entity    Multibanco entity (5 digits), null for MB WAY
    /// @param reference Multibanco reference (9 digits), null for MB WAY
    record Reference(String providerPaymentId, @Nullable String entity, @Nullable String reference) {

        /// An over-long or malformed value is a refusal of the provider answer, not a database error.
        public Reference {
            if (providerPaymentId.isEmpty() || providerPaymentId.length() > 100
                    || entity != null && !entity.matches("[0-9]{5}")
                    || reference != null && !reference.matches("[0-9]{9}")) {
                throw new ProviderRejectedException("provider-invalid-response",
                        "The provider answered with a value of the wrong size");
            }
        }
    }

    /// A webhook call as received: header names in lower case, the raw body.
    record WebhookCall(Map<String, String> headers, byte[] body) {
    }

    /// A verified event: what the provider says happened to one of its payments.
    record Event(String eventId, String providerPaymentId, ProviderPaymentState state, long amountCents) {
    }

    /// @throws ProviderRejectedException    when the provider refuses
    /// @throws ProviderUnavailableException when it cannot be reached
    Reference createMultibanco(MultibancoRequest request);

    /// @throws ProviderRejectedException    when the provider refuses (for example MB WAY is not active on the number)
    /// @throws ProviderUnavailableException when it cannot be reached
    Reference createMbWay(MbWayRequest request);

    /// Best effort: asks the provider to stop waiting for the payment.
    void cancel(String providerPaymentId);

    /// What the provider says about the payment now.
    ProviderPaymentState status(String providerPaymentId);

    /// Checks the call came from the provider (signature, age) and reads the event.
    ///
    /// @throws InvalidWebhookSignatureException when it is not signed by the provider or is too old
    /// @throws InvalidWebhookBodyException      when the signed body cannot be read
    Event verifyWebhook(WebhookCall call);
}
