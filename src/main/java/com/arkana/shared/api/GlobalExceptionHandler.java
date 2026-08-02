package com.arkana.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
    private static final String ERROR_MESSAGE = "ERR [{} {}] {} {} - {}";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> apiException(ApiException exception, HttpServletRequest request) {
        logException(request, exception.status(), exception);
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage(), exception.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Optional<FieldError> error = exception.getBindingResult().getFieldErrors().stream().findFirst();
        Map<String, Object> details = error.<Map<String, Object>>map(field -> Map.of("field", field.getField()))
                .orElse(null);
        String message = error
                .map(field -> field.getDefaultMessage() == null ? "Invalid request." : field.getDefaultMessage())
                .orElse("Invalid request.");
        logException(request, HttpStatus.BAD_REQUEST, exception);
        return ResponseEntity.badRequest().body(new ApiError("INVALID_FIELD", message, details));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalidRequest(Exception exception, HttpServletRequest request) {
        logException(request, HttpStatus.BAD_REQUEST, exception);
        return ResponseEntity.badRequest().body(new ApiError("INVALID_FIELD", "The request is invalid."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> constraintViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        logException(request, HttpStatus.CONFLICT, exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("RESOURCE_CONFLICT", "The operation conflicts with an existing resource."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> resourceNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        logException(request, HttpStatus.NOT_FOUND, exception);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("RESOURCE_NOT_FOUND", "Resource not found."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        logException(request, HttpStatus.INTERNAL_SERVER_ERROR, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred."));
    }

    private void logException(HttpServletRequest request, HttpStatus status, Exception exception) {
        if (status.is5xxServerError()) {
            log.error(
                    ERROR_MESSAGE,
                    request.getMethod(),
                    request.getRequestURI(),
                    status.value(),
                    status.getReasonPhrase(),
                    exception.getMessage(),
                    exception);
        } else if (status == HttpStatus.UNAUTHORIZED) {
            log.debug(
                    ERROR_MESSAGE,
                    request.getMethod(),
                    request.getRequestURI(),
                    status.value(),
                    status.getReasonPhrase(),
                    exception.getMessage());
        } else {
            log.warn(
                    ERROR_MESSAGE,
                    request.getMethod(),
                    request.getRequestURI(),
                    status.value(),
                    status.getReasonPhrase(),
                    exception.getMessage());
        }
    }
}
