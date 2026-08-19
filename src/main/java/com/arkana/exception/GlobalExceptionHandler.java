package com.arkana.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Optional;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  public static final String ERROR_MESSAGE = "ERR [{} {}] {} {} - {}";

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Optional<FieldError> error = exception.getBindingResult().getFieldErrors().stream().findFirst();
    String detail = error
        .map(field -> field.getDefaultMessage() == null ? "Invalid request." : field.getDefaultMessage())
        .orElse("Invalid request.");
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Object> constraintViolation(ConstraintViolationException exception, WebRequest request) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request is invalid.");
    return handleExceptionInternal(exception, problem, HttpHeaders.EMPTY, status, request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Object> dataIntegrityViolation(DataIntegrityViolationException exception, WebRequest request) {
    HttpStatus status = HttpStatus.CONFLICT;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        status,
        "The operation conflicts with an existing resource.");
    return handleExceptionInternal(exception, problem, HttpHeaders.EMPTY, status, request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> accessDenied(AccessDeniedException exception, WebRequest request) {
    HttpStatus status = HttpStatus.FORBIDDEN;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        status,
        "The authenticated user cannot perform this operation.");
    return handleExceptionInternal(exception, problem, HttpHeaders.EMPTY, status, request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "An unexpected error occurred.");
    return handleExceptionInternal(exception, problem, HttpHeaders.EMPTY, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem;
    if (body instanceof ProblemDetail problemDetail) {
      problem = problemDetail;
    } else if (exception instanceof ErrorResponse errorResponse) {
      problem = errorResponse.getBody();
    } else {
      problem = ProblemDetail.forStatusAndDetail(status, defaultDetail(status));
    }
    HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
    problem.setInstance(URI.create(servletRequest.getRequestURI()));
    logException(servletRequest, status, exception, problem.getDetail());

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.putAll(headers);
    responseHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
    return new ResponseEntity<>(problem, responseHeaders, status);
  }

  private String defaultDetail(HttpStatusCode status) {
    HttpStatus httpStatus = HttpStatus.resolve(status.value());
    return httpStatus == null ? "The request could not be processed." : httpStatus.getReasonPhrase();
  }

  private void logException(
      HttpServletRequest request,
      HttpStatusCode status,
      Exception exception,
      String detail) {
    HttpStatus httpStatus = HttpStatus.resolve(status.value());
    String reason = httpStatus == null ? "HTTP " + status.value() : httpStatus.getReasonPhrase();
    if (status.is5xxServerError()) {
      log.error(
          ERROR_MESSAGE,
          request.getMethod(),
          request.getRequestURI(),
          status.value(),
          reason,
          detail,
          exception);
    } else if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
      log.debug(
          ERROR_MESSAGE,
          request.getMethod(),
          request.getRequestURI(),
          status.value(),
          reason,
          detail);
    } else {
      log.warn(
          ERROR_MESSAGE,
          request.getMethod(),
          request.getRequestURI(),
          status.value(),
          reason,
          detail);
    }
  }
}
