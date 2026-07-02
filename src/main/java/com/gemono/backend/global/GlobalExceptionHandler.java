package com.gemono.backend.global;

import com.gemono.backend.service.GroqService;
import com.gemono.backend.data.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

// Centralized error handling — returns ApiResponse format for all exceptions
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Groq rate limit — return 429 so frontend can display a toast warning
    @ExceptionHandler(GroqService.RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleGroqRateLimit(GroqService.RateLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.fail(e.getMessage()));
    }

    // Handles explicit Spring ResponseStatusException (e.g., 404 NOT_FOUND, 403 FORBIDDEN)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.fail(e.getReason()));
    }

    // Handles bad request exceptions
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    // Fallback for unexpected internal server errors (true 500s)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(e.getMessage()));
    }

    // Handles DTO validation framework constraints (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.fail(errors));
    }
}