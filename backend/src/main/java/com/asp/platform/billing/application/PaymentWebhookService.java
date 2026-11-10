package com.asp.platform.billing.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.billing.domain.InvoiceRepository;
import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.PaymentEventRepository;
import com.asp.platform.billing.domain.PaymentProvider;
import com.asp.platform.billing.domain.PaymentRepository;
import com.asp.platform.billing.domain.UnknownPaymentProviderException;

/// The provider's webhook: verified by the provider adapter before anything is read, applied once per event id, and
/// answered with a result even when the event is of no use (so the provider stops sending it). The workshop is the
/// one of the URL (its row-level security scope), never anything from the body.
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    /// What became of a call.
    public enum Result {
        APPLIED,
        DUPLICATE,
        IGNORED
    }

    private final PaymentProvider provider;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final PaymentEventRepository events;
    private final PaymentSettling settling;
    private final Clock clock;

    PaymentWebhookService(PaymentProvider provider, PaymentRepository payments, InvoiceRepository invoices,
            PaymentEventRepository events,
            PaymentSettling settling, Clock clock) {
        this.provider = provider;
        this.payments = payments;
        this.invoices = invoices;
        this.events = events;
        this.settling = settling;
        this.clock = clock;
    }

    /// @param providerId the `{provider}` of the URL
    /// @throws UnknownPaymentProviderException                               when it is not the configured provider
    /// @throws com.asp.platform.billing.domain.InvalidWebhookSignatureException when the call is not the provider's
    /// @throws com.asp.platform.billing.domain.InvalidWebhookBodyException     when the signed body cannot be read
    @Transactional
    public Result handle(UUID tenantId, String providerId, PaymentProvider.WebhookCall call) {
        if (!provider.configured() || !provider.id().equals(providerId)) {
            throw new UnknownPaymentProviderException();
        }
        PaymentProvider.Event event = provider.verifyWebhook(call);
        events.lock(providerId, event.eventId());
        if (events.exists(providerId, event.eventId())) {
            return Result.DUPLICATE;
        }
        Optional<Payment> found = payments.refOfProviderPayment(providerId, event.providerPaymentId())
                .flatMap(ref -> {
                    invoices.lockById(ref.invoiceId());
                    return payments.lockById(ref.id());
                });
        if (found.isEmpty()) {
            return ignored(providerId, event, null, "UNKNOWN_PAYMENT");
        }
        Payment payment = found.get();
        if (payment.amountCents() != event.amountCents()) {
            return ignored(providerId, event, payment.id(), "AMOUNT_MISMATCH");
        }
        if (!settling.settle(tenantId, payment, event.state())) {
            return ignored(providerId, event, payment.id(), "NOT_APPLICABLE");
        }
        events.record(providerId, event.eventId(), payment.id(), true, null, clock.instant());
        return Result.APPLIED;
    }

    /// Stored and logged with ids only (no body, no personal data).
    private Result ignored(String providerId, PaymentProvider.Event event, @Nullable UUID paymentId, String why) {
        events.record(providerId, event.eventId(), paymentId, false, why, clock.instant());
        log.warn("Payment webhook ignored provider={} event={} reason={}", providerId, event.eventId(), why);
        return Result.IGNORED;
    }
}
