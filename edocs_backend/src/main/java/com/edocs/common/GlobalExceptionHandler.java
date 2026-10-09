package com.edocs.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        return respond(ex.getStatus(), ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var first = ex.getBindingResult().getFieldErrors().stream().findFirst();
        String message = first.map(e -> e.getField() + " " + e.getDefaultMessage()).orElse("The request is invalid.");
        return respond(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> handleUnreadable(Exception ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "The request body or parameters are malformed.", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, "Your role does not allow this action.", req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, "Someone else changed this record. Reload and try again.", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "The change conflicts with existing data.", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "Not found.", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Try again in a moment.", req);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), message, req.getRequestURI()));
    }
}
