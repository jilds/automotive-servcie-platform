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
import com.asp.platform.workshop.domain.PhotoTooLargeException;
import com.asp.platform.workshop.domain.PhotoTypeNotAllowedException;

/// Workshop-specific error mappings. An invalid mechanic is reported like a validation error on the
/// mechanic field, with its own code. A photo that is too large (413) or not an image (415) is not a conflict, so it
/// has its own statuses. The servlet's multipart limit is answered by the global handler as `upload-too-large`.
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

    @ExceptionHandler(PhotoTooLargeException.class)
    ProblemDetail photoTooLarge(PhotoTooLargeException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, PhotoTooLargeException.CODE, e.getMessage());
    }

    @ExceptionHandler(PhotoTypeNotAllowedException.class)
    ProblemDetail photoTypeNotAllowed(PhotoTypeNotAllowedException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, PhotoTypeNotAllowedException.CODE, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + code));
        problem.setProperty("code", code);
        return problem;
    }
}
