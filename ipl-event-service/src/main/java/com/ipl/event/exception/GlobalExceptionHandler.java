package com.ipl.event.exception;

import com.ipl.event.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "VALIDATION_ERROR", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED", request);
    }

    @ExceptionHandler(UnauthorizedFranchiseException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedFranchise(UnauthorizedFranchiseException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), "NOT_HOME_FRANCHISE", request);
    }

    @ExceptionHandler({MatchNotFoundException.class, FranchiseNotFoundException.class, StadiumNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND", request);
    }

    @ExceptionHandler(InvalidMatchStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidMatchStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "INVALID_MATCH_STATE", request);
    }

    @ExceptionHandler(ConfigurationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(ConfigurationAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "CONFIGURATION_ALREADY_EXISTS", request);
    }

    @ExceptionHandler(InvalidTicketConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTicketConfig(InvalidTicketConfigurationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_TICKET_CONFIGURATION", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_ERROR", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String errorCode, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(message, errorCode, request.getRequestURI()));
    }
}
