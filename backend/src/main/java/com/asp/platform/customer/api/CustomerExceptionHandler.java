package com.asp.platform.customer.api;

import java.net.URI;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.asp.platform.customer.domain.InvalidPlateException;

/// Customer-specific error mappings. A plate that does not fit its country's format is reported like a
/// validation error on `plate`, with its own code so the UI can explain the expected layouts.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class CustomerExceptionHandler {

    @ExceptionHandler(InvalidPlateException.class)
    ProblemDetail invalidPlate(InvalidPlateException e) {
        String detail = String.valueOf(e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("/problems/" + InvalidPlateException.CODE));
        problem.setProperty("code", InvalidPlateException.CODE);
        problem.setProperty("country", e.country());
        problem.setProperty("errors", Map.of("plate", detail));
        return problem;
    }
}
