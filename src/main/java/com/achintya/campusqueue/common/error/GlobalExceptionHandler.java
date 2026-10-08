package com.achintya.campusqueue.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        ApiError error = ApiError.of(
                Instant.now(clock),
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(exception.getStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        ApiError error = new ApiError(
                Instant.now(clock),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(RequestValidationException.class)
    ResponseEntity<ApiError> handleRequestValidation(
            RequestValidationException exception,
            HttpServletRequest request) {
        ApiError error = new ApiError(
                Instant.now(clock),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                exception.getMessage(),
                request.getRequestURI(),
                exception.getFieldErrors());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                Instant.now(clock),
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_REQUEST",
                "Request body is malformed",
                request.getRequestURI()));
    }
}
