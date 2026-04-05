package com.financeapi.service.impl;

import com.financeapi.domain.RefreshToken;
import com.financeapi.domain.Role;
import com.financeapi.domain.User;
import com.financeapi.dto.request.LoginRequest;
import com.financeapi.dto.request.RefreshTokenRequest;
import com.financeapi.dto.request.RegisterRequest;
import com.financeapi.dto.response.AuthResponse;
import com.financeapi.exception.BadRequestException;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.RefreshTokenRepository;
import com.financeapi.repository.RoleRepository;
import com.financeapi.repository.UserRepository;
import com.financeapi.security.JwtUtils;
import com.financeapi.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${jwt.refresh-token-pepper}")
    private String refreshTokenPepper;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()))
            throw new BadRequestException("Email already registered");

        Role viewerRole = roleRepository.findByName(Role.RoleName.VIEWER)
                .orElseThrow(() -> new ResourceNotFoundException("Role VIEWER not found"));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRoles(Set.of(viewerRole));
        userRepository.save(user);

        return issueTokenPair(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        refreshTokenRepository.revokeAllByUserId(user.getId());
        return issueTokenPair(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BadRequestException("Refresh token expired or revoked");

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokenPair(stored.getUser());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .ifPresent(t -> { t.setRevoked(true); refreshTokenRepository.save(t); });
    }

    private AuthResponse issueTokenPair(User user) {
        java.util.List<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(java.util.stream.Collectors.toList());
        String accessToken = jwtUtils.generateAccessToken(user.getEmail(), roles);
        String rawRefresh = UUID.randomUUID().toString();

        RefreshToken rt = new RefreshToken();
        rt.setTokenHash(hashToken(rawRefresh));
        rt.setUser(user);
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000));
        refreshTokenRepository.save(rt);

        return new AuthResponse(accessToken, rawRefresh);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((token + refreshTokenPepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
