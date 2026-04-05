package com.financeapi.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    private final SecretKey key;
    private final long accessTokenExpiry;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.access-token-expiry}") long accessTokenExpiry) {
        // Strip surrounding quotes that may come from env var misconfiguration
        secret = secret.trim().replaceAll("^\"|\"$", "");

        byte[] keyBytes;
        // Try base64 first (handles secrets generated with openssl rand -base64 32)
        try {
            keyBytes = java.util.Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            // Fall back to raw UTF-8 bytes
            keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32)
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes (256 bits)");
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiry = accessTokenExpiry;
    }

    public String generateAccessToken(String email, java.util.Collection<String> roles) {
        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
