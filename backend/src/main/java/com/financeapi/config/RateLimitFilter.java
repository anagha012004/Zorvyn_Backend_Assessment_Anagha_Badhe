package com.financeapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.local.LocalBucketBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // In-memory buckets per user — simple and reliable on single instance
    private final ConcurrentHashMap<String, io.github.bucket4j.Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            chain.doFilter(request, response);
            return;
        }

        String key = auth.getName();
        var bucket = buckets.computeIfAbsent(key, k -> buildBucket(auth));

        if (bucket.tryConsume(1)) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
        }
    }

    private io.github.bucket4j.Bucket buildBucket(Authentication auth) {
        boolean isAdmin   = hasRole(auth, "ROLE_ADMIN");
        boolean isAnalyst = hasRole(auth, "ROLE_ANALYST");
        long capacity = isAdmin ? 500 : isAnalyst ? 200 : 100;
        return io.github.bucket4j.Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1)).build())
                .build();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }
}
