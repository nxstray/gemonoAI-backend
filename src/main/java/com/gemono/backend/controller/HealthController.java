package com.gemono.backend.controller;

import com.gemono.backend.data.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Service health checks")
public class HealthController {

    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping
    @Operation(summary = "Overall health status")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "UP", "service", "gemono-backend")));
    }

    @GetMapping("/redis")
    @Operation(summary = "Check Redis connectivity")
    public ResponseEntity<ApiResponse<Map<String, String>>> redisHealth() {
        try {
            // Ping Redis with a test write/read
            redisTemplate.opsForValue().set("health:ping", "pong");
            String result = redisTemplate.opsForValue().get("health:ping");

            if ("pong".equals(result)) {
                return ResponseEntity.ok(ApiResponse.ok(Map.of("redis", "UP")));
            }
            return ResponseEntity.status(503).body(ApiResponse.fail("Redis read mismatch"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(ApiResponse.fail("Redis DOWN: " + e.getMessage()));
        }
    }
}