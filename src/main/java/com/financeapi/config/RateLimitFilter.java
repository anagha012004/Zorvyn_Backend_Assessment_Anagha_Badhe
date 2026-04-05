package com.financeapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
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

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            chain.doFilter(request, response);
            return;
        }

        String key = auth.getName();
        Bucket bucket = buckets.computeIfAbsent(key, k -> buildBucket(auth));

        if (bucket.tryConsume(1)) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
        }
    }

    private Bucket buildBucket(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals("ROLE_ADMIN"));
        boolean isAnalyst = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals("ROLE_ANALYST"));

        long capacity = isAdmin ? 500 : isAnalyst ? 200 : 100;
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(1)).build())
                .build();
    }
}
