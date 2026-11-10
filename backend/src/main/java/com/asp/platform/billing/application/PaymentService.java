package com.asp.platform.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoiceNotFoundException;
import com.asp.platform.billing.domain.InvoiceNotIssuedException;
import com.asp.platform.billing.domain.InvoiceRepository;
import com.asp.platform.billing.domain.InvalidPhoneException;
import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.PaymentExceedsBalanceException;
import com.asp.platform.billing.domain.PaymentFailedException;
import com.asp.platform.billing.domain.PaymentMethod;
import com.asp.platform.billing.domain.PaymentNotFoundException;
import com.asp.platform.billing.domain.PaymentNotPendingException;
import com.asp.platform.billing.domain.PaymentProvider;
import com.asp.platform.billing.domain.PaymentRepository;
import com.asp.platform.billing.domain.PaymentStatus;
import com.asp.platform.billing.domain.ProviderNotConfiguredException;
import com.asp.platform.billing.domain.ProviderPaymentState;
import com.asp.platform.billing.domain.ProviderRejectedException;
import com.asp.platform.billing.domain.ProviderUnavailableException;
import com.asp.platform.billing.domain.Settlement;
import com.asp.platform.shared.CurrentUser;

/// Payments of an invoice: money taken by staff (PAID at once) and Multibanco references and MB WAY requests through the
/// payment provider (PENDING until the provider says more). A payment is created under the invoice row lock, with the
/// provider called inside it: if the provider refuses or is down nothing is stored.
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Pattern PT_MOBILE = Pattern.compile("^(?:\\+351|00351)?(9\\d{8})$");

    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final PaymentProvider provider;
    private final PaymentSettling settling;
    private final BillingProperties properties;
    private final Clock clock;

    PaymentService(PaymentRepository payments, InvoiceRepository invoices, PaymentProvider provider,
            PaymentSettling settling, BillingProperties properties, Clock clock) {
        this.payments = payments;
        this.invoices = invoices;
        this.provider = provider;
        this.settling = settling;
        this.properties = properties;
        this.clock = clock;
    }

    /// @param amountCents the amount, or null for what is left of the invoice
    /// @param phone       the MB WAY phone number (required for MBWAY, not stored)
    /// @throws InvoiceNotFoundException        for an unknown invoice
    /// @throws InvoiceNotIssuedException       unless the invoice is ISSUED and has no credit note
    /// @throws PaymentExceedsBalanceException  when the amount is not between 1 and what is left
    /// @throws InvalidPhoneException           for an MB WAY phone that is not a Portuguese mobile
    /// @throws ProviderNotConfiguredException  for Multibanco or MB WAY without a payment provider
    /// @throws PaymentFailedException          when the provider refuses
    /// @throws ProviderUnavailableException    when the provider cannot be reached
    @Transactional
    public Payment create(CurrentUser user, UUID invoiceId, PaymentMethod method, @Nullable Long amountCents,
            @Nullable String phone, @Nullable String note) {
        Invoice invoice = invoices.lockById(invoiceId).orElseThrow(InvoiceNotFoundException::new);
        invoice.requireIssuedInvoice();
        if (invoices.findActiveCreditNote(invoiceId).isPresent()) {
            throw new InvoiceNotIssuedException();
        }
        long left = Settlement.left(invoice.totalCents(), payments.findByInvoice(invoiceId));
        long amount = amountCents == null ? left : amountCents;
        if (amount < 1 || amount > left) {
            throw new PaymentExceedsBalanceException(left);
        }
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        Payment payment;
        if (!method.viaProvider()) {
            payment = Payment.recorded(id, invoiceId, method, amount, cleanNote, user.userId(), now);
        } else {
            payment = createViaProvider(id, invoiceId, method, amount, phone, cleanNote, user.userId(), now);
        }
        Payment saved = payments.save(payment);
        if (saved.status() == PaymentStatus.PAID) {
            settling.publishReceived(user.tenantId(), saved);
        }
        return saved;
    }

    private Payment createViaProvider(UUID id, UUID invoiceId, PaymentMethod method, long amount,
            @Nullable String phone, @Nullable String note, UUID by, Instant now) {
        String normalized = method == PaymentMethod.MBWAY ? normalizePhone(phone) : null;
        if (!provider.configured()) {
            throw new ProviderNotConfiguredException();
        }
        try {
            if (method == PaymentMethod.MULTIBANCO) {
                Instant expires = now.plus(properties.multibancoValidDays(), ChronoUnit.DAYS);
                PaymentProvider.Reference ref = provider.createMultibanco(
                        new PaymentProvider.MultibancoRequest(id, amount, expires));
                return Payment.multibanco(id, invoiceId, amount, provider.id(), ref.providerPaymentId(),
                        java.util.Objects.requireNonNull(ref.entity()), java.util.Objects.requireNonNull(ref.reference()),
                        expires, note, by, now);
            }
            Instant expires = now.plus(properties.mbwayValidMinutes(), ChronoUnit.MINUTES);
            PaymentProvider.Reference ref = provider.createMbWay(
                    new PaymentProvider.MbWayRequest(id, java.util.Objects.requireNonNull(normalized), amount, expires));
            return Payment.mbway(id, invoiceId, amount, provider.id(), ref.providerPaymentId(), mask(normalized),
                    expires, note, by, now);
        } catch (ProviderRejectedException e) {
            throw new PaymentFailedException(e.code());
        }
    }

    /// @throws PaymentNotFoundException for an unknown payment
    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(PaymentNotFoundException::new);
    }

    /// Stops waiting for a pending payment (the customer will not pay with this reference).
    ///
    /// @throws com.asp.platform.shared.StaleDataException for another stored version
    /// @throws PaymentNotPendingException                 unless PENDING
    @Transactional
    public Payment cancel(UUID id, long expectedVersion) {
        Payment payment = lockPayment(id).orElseThrow(PaymentNotFoundException::new);
        payment.requireVersion(expectedVersion);
        payment.requirePending();
        String providerPaymentId = payment.providerPaymentId();
        if (providerPaymentId != null) {
            try {
                provider.cancel(providerPaymentId);
            } catch (RuntimeException e) {
                log.warn("Provider cancel failed payment={} error={}", id, e.getClass().getSimpleName());
            }
        }
        payment.cancel();
        return payments.save(payment);
    }

    /// Asks the provider for the status of a pending payment and applies it (a lost webhook).
    ///
    /// @throws PaymentNotPendingException   unless PENDING
    /// @throws ProviderUnavailableException when the provider cannot be reached
    @Transactional
    public Payment refresh(UUID tenantId, UUID id) {
        Payment payment = lockPayment(id).orElseThrow(PaymentNotFoundException::new);
        payment.requirePending();
        String providerPaymentId = java.util.Objects.requireNonNull(payment.providerPaymentId());
        settling.settle(tenantId, payment, provider.status(providerPaymentId));
        return payments.findById(id).orElseThrow(PaymentNotFoundException::new);
    }

    /// Sandbox only: does to the payment what the provider would. PAID also applies to an expired or cancelled one.
    ///
    /// @throws PaymentNotPendingException when the payment cannot change that way
    @Transactional
    public Payment simulate(UUID tenantId, UUID id, ProviderPaymentState outcome) {
        Payment payment = lockPayment(id).orElseThrow(PaymentNotFoundException::new);
        if (!settling.settle(tenantId, payment, outcome)) {
            throw new PaymentNotPendingException();
        }
        return payments.findById(id).orElseThrow(PaymentNotFoundException::new);
    }

    /// PENDING payments past their validity.
    @Transactional(readOnly = true)
    public List<PaymentRepository.Cursor> dueForExpiry(Instant now, PaymentRepository.@Nullable Cursor after, int limit) {
        return payments.findDueForExpiry(now, after, limit);
    }

    /// Expires one payment whose validity ended, after asking the provider (a lost webhook may hide a payment).
    /// Leaves it alone when the provider cannot be asked.
    ///
    /// @return true when the payment changed
    @Transactional
    public boolean expireOne(UUID tenantId, UUID id, Instant now) {
        Optional<Payment> found = lockPayment(id);
        if (found.isEmpty() || !found.get().isPending() || found.get().expiresAt() == null
                || !found.get().expiresAt().isBefore(now)) {
            return false;
        }
        Payment payment = found.get();
        ProviderPaymentState state = ProviderPaymentState.PENDING;
        String providerPaymentId = payment.providerPaymentId();
        if (providerPaymentId != null) {
            state = provider.status(providerPaymentId);
        }
        if (state == ProviderPaymentState.PENDING) {
            state = ProviderPaymentState.EXPIRED;
        }
        return settling.settle(tenantId, payment, state);
    }

    /// Locks a payment the way every flow does: its invoice row first, then the payment (the order a void and a
    /// late provider event must agree on, so they wait for each other instead of deadlocking). The payment is read
    /// again after the locks, so what it sees is what the other flow committed.
    private Optional<Payment> lockPayment(UUID id) {
        Optional<PaymentRepository.Ref> ref = payments.refOf(id);
        if (ref.isEmpty()) {
            return Optional.empty();
        }
        invoices.lockById(ref.get().invoiceId());
        return payments.lockById(id);
    }

    /// "912345678", "+351 912 345 678" or "00351912345678" become "+351912345678".
    static String normalizePhone(@Nullable String phone) {
        if (phone == null) {
            throw new InvalidPhoneException();
        }
        Matcher matcher = PT_MOBILE.matcher(phone.replaceAll("[\\s-]", ""));
        if (!matcher.matches()) {
            throw new InvalidPhoneException();
        }
        return "+351" + matcher.group(1);
    }

    /// "+351912345678" becomes "+351 9** *** 678".
    static String mask(String normalized) {
        String national = normalized.substring(4);
        return "+351 " + national.charAt(0) + "** *** " + national.substring(6);
    }
}
