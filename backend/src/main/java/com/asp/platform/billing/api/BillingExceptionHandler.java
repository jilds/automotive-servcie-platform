package com.asp.platform.billing.api;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.asp.platform.billing.domain.AlreadyInvoicedException;
import com.asp.platform.billing.domain.InvalidWebhookSignatureException;
import com.asp.platform.billing.domain.PaymentExceedsBalanceException;
import com.asp.platform.billing.domain.PaymentFailedException;
import com.asp.platform.billing.domain.ProviderUnavailableException;
import com.asp.platform.billing.domain.WebhookTooLargeException;
import com.asp.platform.billing.domain.WorkOrderNotInvoiceableException;

/// Billing errors with extra properties (the invoice that already exists, what is left to pay) or another status than
/// the global handler gives (401 for a bad webhook signature, 503 for a provider that does not answer).
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class BillingExceptionHandler {

    @ExceptionHandler(AlreadyInvoicedException.class)
    ProblemDetail alreadyInvoiced(AlreadyInvoicedException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
        problem.setProperty("invoiceId", e.invoiceId());
        problem.setProperty("invoiceStatus", e.invoiceStatus().name());
        return problem;
    }

    @ExceptionHandler(WorkOrderNotInvoiceableException.class)
    ProblemDetail notInvoiceable(WorkOrderNotInvoiceableException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
        problem.setProperty("status", e.status());
        return problem;
    }

    @ExceptionHandler(PaymentExceedsBalanceException.class)
    ProblemDetail exceedsBalance(PaymentExceedsBalanceException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
        problem.setProperty("leftCents", e.leftCents());
        return problem;
    }

    @ExceptionHandler(PaymentFailedException.class)
    ProblemDetail paymentFailed(PaymentFailedException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
        problem.setProperty("reason", e.reason());
        return problem;
    }

    /// The provider did not answer. Nothing was stored for this call; the client may try again.
    @ExceptionHandler(ProviderUnavailableException.class)
    ProblemDetail providerUnavailable(ProviderUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ProviderUnavailableException.CODE,
                "The provider cannot be reached right now; try again");
    }

    @ExceptionHandler(WebhookTooLargeException.class)
    ProblemDetail webhookTooLarge(WebhookTooLargeException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, WebhookTooLargeException.CODE, e.getMessage());
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    ProblemDetail invalidSignature(InvalidWebhookSignatureException e) {
        return problem(HttpStatus.UNAUTHORIZED, InvalidWebhookSignatureException.CODE, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + code));
        problem.setProperty("code", code);
        return problem;
    }
}
