package com.asp.platform.identity.api;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.asp.platform.identity.domain.InvalidCredentialsException;
import com.asp.platform.identity.domain.InvalidLogoException;
import com.asp.platform.identity.domain.WorkshopSuspendedException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdentityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "invalid-credentials", e.getMessage());
    }

    /// Right password, but the workshop's subscription is suspended or cancelled.
    @ExceptionHandler(WorkshopSuspendedException.class)
    ProblemDetail workshopSuspended(WorkshopSuspendedException e) {
        return problem(HttpStatus.FORBIDDEN, WorkshopSuspendedException.CODE, e.getMessage());
    }

    @ExceptionHandler(InvalidLogoException.class)
    ProblemDetail invalidLogo(InvalidLogoException e) {
        HttpStatus status = InvalidLogoException.TOO_LARGE.equals(e.code())
                ? HttpStatus.CONTENT_TOO_LARGE : HttpStatus.BAD_REQUEST;
        return problem(status, e.code(), e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + code));
        problem.setProperty("code", code);
        return problem;
    }
}
