package com.extradict.fintechapi.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // One bucket per IP address
    // ConcurrentHashMap is thread-safe — multiple requests can come simultaneously
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Get client IP
        String ip = getClientIp(request);

        // Get or create bucket for this IP
        Bucket bucket = buckets.computeIfAbsent(ip, this::createNewBucket);

        // Try to take 1 token
        if (bucket.tryConsume(1)) {
            // Token available → allow request through
            filterChain.doFilter(request, response);
        } else {
            // No tokens left → reject with 429
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"error\":\"Too many requests\"," +
                "\"message\":\"Rate limit exceeded. Try again in 1 minute.\"," +
                "\"retryAfter\":60}"
            );
        }
    }

    private Bucket createNewBucket(String ip) {
        // 20 requests per minute per IP
        // Refill 20 tokens every 1 minute
        Bandwidth limit = Bandwidth.classic(
            20,
            Refill.intervally(20, Duration.ofMinutes(1))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header first (for requests behind proxy/load balancer)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}