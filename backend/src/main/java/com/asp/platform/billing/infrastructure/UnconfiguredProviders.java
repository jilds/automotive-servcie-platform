package com.asp.platform.billing.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoicingProvider;
import com.asp.platform.billing.domain.DocumentDraft;
import com.asp.platform.billing.domain.InvalidWebhookSignatureException;
import com.asp.platform.billing.domain.IssuedDocument;
import com.asp.platform.billing.domain.PaymentProvider;
import com.asp.platform.billing.domain.ProviderNotConfiguredException;
import com.asp.platform.billing.domain.ProviderPaymentState;

/// What stands in when a provider is `none` (the default of the prod profile until a real provider is chosen): every
/// call that would reach it answers 409 `provider-not-configured`.
@Configuration(proxyBeanMethods = false)
class UnconfiguredProviders {

    @Bean
    @ConditionalOnExpression("!'${app.billing.invoicing.provider:sandbox}'.equals('sandbox')")
    InvoicingProvider unconfiguredInvoicing() {
        return new InvoicingProvider() {
            @Override
            public String id() {
                return "none";
            }

            @Override
            public boolean configured() {
                return false;
            }

            @Override
            public boolean sandbox() {
                return false;
            }

            @Override
            public IssuedDocument issue(DocumentDraft draft) {
                throw new ProviderNotConfiguredException();
            }

            @Override
            public void voidDocument(String providerDocumentId, String reason) {
                throw new ProviderNotConfiguredException();
            }

            @Override
            public byte[] pdf(Invoice document) {
                throw new ProviderNotConfiguredException();
            }
        };
    }

    @Bean
    @ConditionalOnExpression("!'${app.billing.payments.provider:sandbox}'.equals('sandbox')")
    PaymentProvider unconfiguredPayments() {
        return new PaymentProvider() {
            @Override
            public String id() {
                return "none";
            }

            @Override
            public boolean configured() {
                return false;
            }

            @Override
            public boolean sandbox() {
                return false;
            }

            @Override
            public Reference createMultibanco(MultibancoRequest request) {
                throw new ProviderNotConfiguredException();
            }

            @Override
            public Reference createMbWay(MbWayRequest request) {
                throw new ProviderNotConfiguredException();
            }

            @Override
            public void cancel(String providerPaymentId) {
                // Nothing was ever created.
            }

            @Override
            public ProviderPaymentState status(String providerPaymentId) {
                throw new ProviderNotConfiguredException();
            }

            @Override
            public Event verifyWebhook(WebhookCall call) {
                throw new InvalidWebhookSignatureException();
            }
        };
    }
}
