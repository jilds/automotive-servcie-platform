package com.asp.platform.reporting.api;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.asp.platform.reporting.domain.ReportBusyException;

/// Reporting-specific error mappings: a busy report slot is 429 `try-again` with a `Retry-After` of a few seconds.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ReportingExceptionHandler {

    private static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(ReportBusyException.class)
    ResponseEntity<ProblemDetail> busy(ReportBusyException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setType(URI.create("/problems/" + ReportBusyException.CODE));
        problem.setProperty("code", ReportBusyException.CODE);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }
}
