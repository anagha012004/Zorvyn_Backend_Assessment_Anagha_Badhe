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
        byte[] keyBytes = secret.length() >= 64 && secret.matches("[0-9a-fA-F]+")
                ? hexToBytes(secret)
                : secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }
}
