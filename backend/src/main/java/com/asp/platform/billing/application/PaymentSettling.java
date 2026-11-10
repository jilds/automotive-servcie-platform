package com.asp.platform.billing.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.asp.platform.billing.PaymentReceived;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoiceRepository;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.PaymentProvider;
import com.asp.platform.billing.domain.PaymentRepository;
import com.asp.platform.billing.domain.PaymentStatus;
import com.asp.platform.billing.domain.ProviderPaymentState;

/// What happens to a payment when the provider says something about it, or when its invoice is closed. Runs inside
/// the caller's transaction (the caller has locked the payment or the invoice).
@Component
class PaymentSettling {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettling.class);

    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final PaymentProvider provider;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentSettling(PaymentRepository payments, InvoiceRepository invoices, PaymentProvider provider,
            ApplicationEventPublisher events, Clock clock) {
        this.payments = payments;
        this.invoices = invoices;
        this.provider = provider;
        this.events = events;
        this.clock = clock;
    }

    /// Applies the provider's state to the payment and stores it when it changed. A payment that becomes PAID
    /// publishes [PaymentReceived].
    ///
    /// @return true when the payment changed
    boolean settle(UUID tenantId, Payment payment, ProviderPaymentState state) {
        if (!payment.applyProviderState(state, clock.instant())) {
            return false;
        }
        Payment saved = payments.save(payment);
        if (saved.status() == PaymentStatus.PAID) {
            publishReceived(tenantId, saved);
        }
        return true;
    }

    /// Announces a payment that is PAID (also those staff record at once).
    void publishReceived(UUID tenantId, Payment paid) {
        Invoice invoice = invoices.findById(paid.invoiceId()).orElse(null);
        long total = invoice == null ? 0 : invoice.totalCents();
        boolean late = invoice != null && (invoice.status() == InvoiceStatus.VOIDED
                || !invoices.findIssuedCreditNotes(List.of(invoice.id())).isEmpty());
        long received = payments.findByInvoice(paid.invoiceId()).stream()
                .filter(p -> p.status() == PaymentStatus.PAID).mapToLong(Payment::amountCents).sum();
        events.publishEvent(new PaymentReceived(tenantId, paid.invoiceId(), paid.id(), paid.method().name(),
                paid.amountCents(), invoice != null && received >= total, late));
    }

    /// Cancels the pending payments (the invoice was voided or credited) and tells the provider, best effort.
    void cancelPending(List<Payment> existing) {
        for (Payment payment : existing) {
            if (!payment.isPending()) {
                continue;
            }
            String providerPaymentId = payment.providerPaymentId();
            payment.cancel();
            payments.save(payment);
            if (providerPaymentId != null) {
                try {
                    provider.cancel(providerPaymentId);
                } catch (RuntimeException e) {
                    log.warn("Provider cancel failed payment={} error={}", payment.id(), e.getClass().getSimpleName());
                }
            }
        }
    }
}
