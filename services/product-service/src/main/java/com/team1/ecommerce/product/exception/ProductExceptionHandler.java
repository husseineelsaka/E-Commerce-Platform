package com.team1.ecommerce.product.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ProductExceptionHandler {
    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(ProductNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({InvalidPagingException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(), "error", status.getReasonPhrase(),
                "message", message, "path", request.getRequestURI()));
    }
}
