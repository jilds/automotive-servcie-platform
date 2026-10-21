package com.asp.platform.workshop.api;

import java.net.URI;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.asp.platform.workshop.domain.InvalidMechanicException;

/// Workshop-specific error mappings. An invalid mechanic is reported like a validation error on the
/// mechanic field, with its own code.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class WorkshopExceptionHandler {

    @ExceptionHandler(InvalidMechanicException.class)
    ProblemDetail invalidMechanic(InvalidMechanicException e) {
        String detail = String.valueOf(e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("/problems/" + InvalidMechanicException.CODE));
        problem.setProperty("code", InvalidMechanicException.CODE);
        problem.setProperty("errors", Map.of(e.field(), detail));
        return problem;
    }
}
