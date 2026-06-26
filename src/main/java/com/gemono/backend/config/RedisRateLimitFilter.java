package com.gemono.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

// Redis-based rate limiter — persists across restarts, works in multi-instance deploy
// Uses sliding window counter: key = "rl:{ip}", TTL = window seconds
@Component
@RequiredArgsConstructor
public class RedisRateLimitFilter implements Filter {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rate.limit.max-requests:30}")
    private int maxRequests;

    @Value("${rate.limit.window-seconds:60}")
    private long windowSeconds;

    // Only apply to expensive AI endpoints
    private static final String[] RATE_LIMITED_PATHS = {
        "/api/guest/conversations/send",
        "/api/conversations/send"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        if (shouldRateLimit(httpReq.getRequestURI())) {
            String ip = resolveClientIp(httpReq);
            String key = "rl:" + ip;

            try {
                Long count = redisTemplate.opsForValue().increment(key);

                if (count != null && count == 1) {
                    // First request in window — set TTL
                    redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
                }

                if (count != null && count > maxRequests) {
                    HttpServletResponse httpRes = (HttpServletResponse) response;
                    httpRes.setStatus(429);
                    httpRes.setContentType("application/json");
                    httpRes.getWriter().write(
                        "{\"success\":false,\"error\":\"Rate limit exceeded. Please wait before sending another message.\"}"
                    );
                    return;
                }
            } catch (Exception e) {
                // If Redis is down, fail open — do not block the request
                // Log the issue but allow traffic to continue
                System.err.println("[RateLimit] Redis error, failing open: " + e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private boolean shouldRateLimit(String path) {
        for (String p : RATE_LIMITED_PATHS) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}