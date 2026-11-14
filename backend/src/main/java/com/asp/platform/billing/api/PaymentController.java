package com.asp.platform.billing.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.billing.application.PaymentService;
import com.asp.platform.billing.domain.Financier;
import com.asp.platform.billing.domain.Instalment;
import com.asp.platform.billing.domain.PaymentMethod;
import com.asp.platform.shared.CurrentUser;

/// Payments of an invoice. Owner and front desk only.
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
class PaymentController {

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentResponse create(CurrentUser user, @PathVariable UUID invoiceId, @Valid @RequestBody CreatePaymentRequest request) {
        return PaymentResponse.of(payments.create(user, invoiceId, request.method(), request.amountCents(),
                request.phone(), request.note(), request.instalment() == null ? null : request.instalment().toDomain()));
    }

    @GetMapping("/payments/{id}")
    PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.of(payments.get(id));
    }

    @PostMapping("/payments/{id}/refresh")
    PaymentResponse refresh(CurrentUser user, @PathVariable UUID id) {
        return PaymentResponse.of(payments.refresh(user.tenantId(), id));
    }

    @PostMapping("/payments/{id}/cancel")
    PaymentResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelPaymentRequest request) {
        return PaymentResponse.of(payments.cancel(id, request.version()));
    }

    /// `amountCents` left out means what is left of the invoice; `phone` is for MBWAY only; `instalment` is required
    /// for INSTALMENTS and not allowed for the other methods.
    record CreatePaymentRequest(@NotNull PaymentMethod method, @Positive @Nullable Long amountCents,
            @Size(max = 30) @Nullable String phone, @Size(max = 200) @Nullable String note,
            @Valid @Nullable InstalmentRequest instalment) {

        /// The phone number never reaches a log line.
        @Override
        public String toString() {
            return "CreatePaymentRequest[method=" + method + ", amountCents=" + amountCents + "]";
        }
    }

    record InstalmentRequest(@NotNull Financier financier, @NotNull Integer count,
            @Size(max = 60) @Nullable String reference) {

        Instalment toDomain() {
            String ref = reference == null || reference.isBlank() ? null : reference.strip();
            return new Instalment(financier, count, ref);
        }
    }

    record CancelPaymentRequest(@NotNull Long version) {
    }
}
