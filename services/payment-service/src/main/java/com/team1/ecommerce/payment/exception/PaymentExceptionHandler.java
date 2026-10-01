package com.team1.ecommerce.payment.exception;

import com.team1.ecommerce.payment.service.PaymentConflictException;
import com.team1.ecommerce.payment.service.PaymentMismatchException;
import com.team1.ecommerce.payment.service.PaymentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class PaymentExceptionHandler {
    @ExceptionHandler(PaymentConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(Exception error, HttpServletRequest request) {
        return body(HttpStatus.CONFLICT, error.getMessage(), request);
    }

    @ExceptionHandler(PaymentMismatchException.class)
    ResponseEntity<Map<String, Object>> mismatch(Exception error, HttpServletRequest request) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, error.getMessage(), request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<Map<String, Object>> missing(Exception error, HttpServletRequest request) {
        return body(HttpStatus.NOT_FOUND, error.getMessage(), request);
    }

    @ExceptionHandler({MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception error, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, error.getMessage(), request);
    }

    /** Constraint on a parameter (e.g. the Idempotency-Key size) or on the @Valid body when method validation applies. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> invalidParameter(HandlerMethodValidationException error, HttpServletRequest request) {
        ParameterValidationResult result = error.getAllValidationResults().getFirst();
        String message = result instanceof ParameterErrors errors && errors.getFieldError() != null
                ? errors.getFieldError().getField() + " " + errors.getFieldError().getDefaultMessage()
                : result.getMethodParameter().getParameterName() + " " + result.getResolvableErrors().getFirst().getDefaultMessage();
        return body(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException error, HttpServletRequest request) {
        var field = error.getBindingResult().getFieldErrors().getFirst();
        return body(HttpStatus.BAD_REQUEST, field.getField() + " " + field.getDefaultMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", status.getReasonPhrase(),
                "message", message, "path", request.getRequestURI()));
    }
}
