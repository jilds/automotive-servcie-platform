package com.asp.platform.config.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.asp.platform.shared.ConflictException;
import com.asp.platform.shared.InvalidInputException;
import com.asp.platform.shared.NotFoundException;
import com.asp.platform.shared.StaleDataException;

/// Maps errors to RFC 9457 problem responses with a stable `type` and `code`.
/// Feature-specific mappings live in an advice in that feature's `api` package.
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    /// Another request held the rows this one needed to lock for longer than the lock timeout. Nothing was
    /// changed; the client may simply retry.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail tryAgain(PessimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "try-again", "The data is being changed by someone else; try again");
    }

    /// Two saves of the same record raced: both read the same version, the other one was written first. Same
    /// answer as a stale version sent by the client (`StaleDataException`): reload and try again.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail staleData(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, StaleDataException.CODE,
                "Someone else changed this record; reload it and try again");
    }

    /// A domain rule rejected one field: reported like a validation error, with the rule's own code.
    @ExceptionHandler(InvalidInputException.class)
    ProblemDetail invalidInput(InvalidInputException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, e.code(), e.getMessage());
        problem.setProperty("errors", Map.of(e.field(), String.valueOf(e.getMessage())));
        return problem;
    }

    /// Last resort for domain invariants that have no code of their own yet. Rules users can break through the
    /// forms have coded exceptions ([InvalidInputException]), so this mostly guards programming errors.
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", e.getMessage());
    }

    /// Adds the failing fields, so the UI can show the message next to the right input.
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation", "Some fields are not valid");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), String.valueOf(error.getDefaultMessage())));
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /// A multipart upload over the container's `spring.servlet.multipart` limit (5 MB per file): 413 with a neutral
    /// code. A feature with a tighter rule of its own (a logo, a photo) answers with its own code when the file
    /// reaches it.
    @Override
    protected @Nullable ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.CONTENT_TOO_LARGE, "upload-too-large", "The file is too large");
        return handleExceptionInternal(ex, body, headers, HttpStatus.CONTENT_TOO_LARGE, request);
    }

    /// A path or query value of the wrong type, for example an enum name that does not exist: 400 `validation` with
    /// the parameter in `errors`.
    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(org.springframework.beans.TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation", "Some fields are not valid");
        String name = ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException m
                ? m.getName() : String.valueOf(ex.getPropertyName());
        body.setProperty("errors", Map.of(name, "Not a valid value"));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    static ProblemDetail problem(HttpStatus status, String code, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + code));
        problem.setProperty("code", code);
        return problem;
    }
}
