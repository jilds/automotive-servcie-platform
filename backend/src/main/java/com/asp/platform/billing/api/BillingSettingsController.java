package com.asp.platform.billing.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.billing.application.BillingProperties;
import com.asp.platform.billing.domain.InvoicingProvider;
import com.asp.platform.billing.domain.PaymentMethod;
import com.asp.platform.billing.domain.PaymentProvider;

/// Which providers are active, for the screens (the sandbox banner, the payment methods on offer). Read only: the
/// providers come from the environment.
@RestController
@RequestMapping("/api/v1/billing/settings")
@PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
class BillingSettingsController {

    private final InvoicingProvider invoicing;
    private final PaymentProvider payments;
    private final BillingProperties properties;

    BillingSettingsController(InvoicingProvider invoicing, PaymentProvider payments, BillingProperties properties) {
        this.invoicing = invoicing;
        this.payments = payments;
        this.properties = properties;
    }

    record ProviderResponse(String provider, boolean configured, boolean sandbox) {
    }

    record BillingSettingsResponse(ProviderResponse invoicing, ProviderResponse payments, long finalConsumerLimitCents,
            String currency, List<PaymentMethod> methods, int multibancoValidDays, int mbwayValidMinutes) {
    }

    @GetMapping
    BillingSettingsResponse get() {
        List<PaymentMethod> methods = new ArrayList<>(List.of(PaymentMethod.CASH, PaymentMethod.CARD,
                PaymentMethod.TRANSFER));
        if (payments.configured()) {
            methods.add(PaymentMethod.MULTIBANCO);
            methods.add(PaymentMethod.MBWAY);
        }
        return new BillingSettingsResponse(
                new ProviderResponse(invoicing.id(), invoicing.configured(), invoicing.sandbox()),
                new ProviderResponse(payments.id(), payments.configured(), payments.sandbox()),
                properties.finalConsumerLimitCents(), "EUR", methods, properties.multibancoValidDays(),
                properties.mbwayValidMinutes());
    }
}
