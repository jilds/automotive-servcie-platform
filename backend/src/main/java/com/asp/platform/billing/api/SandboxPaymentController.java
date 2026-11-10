package com.asp.platform.billing.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.billing.application.PaymentService;
import com.asp.platform.billing.domain.ProviderPaymentState;
import com.asp.platform.shared.CurrentUser;

/// Does to a payment what the provider would, so a flow can be walked without a real provider. The route exists only
/// while the payment provider is the sandbox: with any other provider it is not registered at all (404).
@RestController
@RequestMapping("/api/v1/payments/{id}/sandbox")
@PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
@ConditionalOnProperty(name = "app.billing.payments.provider", havingValue = "sandbox", matchIfMissing = true)
class SandboxPaymentController {

    private final PaymentService payments;

    SandboxPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/simulate")
    PaymentResponse simulate(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody SimulateRequest request) {
        return PaymentResponse.of(payments.simulate(user.tenantId(), id, request.outcome()));
    }

    /// PAID, FAILED or EXPIRED.
    record SimulateRequest(@NotNull ProviderPaymentState outcome) {
    }
}
