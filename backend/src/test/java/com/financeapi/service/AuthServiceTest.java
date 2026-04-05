package com.financeapi.service;

import com.financeapi.domain.Role;
import com.financeapi.domain.User;
import com.financeapi.dto.request.LoginRequest;
import com.financeapi.dto.request.RegisterRequest;
import com.financeapi.dto.response.AuthResponse;
import com.financeapi.exception.BadRequestException;
import com.financeapi.repository.RefreshTokenRepository;
import com.financeapi.repository.RoleRepository;
import com.financeapi.repository.UserRepository;
import com.financeapi.security.JwtUtils;
import com.financeapi.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtUtils jwtUtils;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;

    @InjectMocks AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 604800000L);
    }

    @Test
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");
        req.setFullName("Test User");

        Role viewerRole = new Role();
        viewerRole.setName(Role.RoleName.VIEWER);

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setEmail(req.getEmail());
        savedUser.setRoles(Set.of(viewerRole));

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(roleRepository.findByName(Role.RoleName.VIEWER)).thenReturn(Optional.of(viewerRole));
        when(passwordEncoder.encode(req.getPassword())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtils.generateAccessToken(eq(req.getEmail()), anyList())).thenReturn("access-token");

        AuthResponse response = authService.register(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@example.com");
        req.setPassword("password123");
        req.setFullName("Test");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void login_success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@finance.com");
        req.setPassword("admin123");

        Role adminRole = new Role();
        adminRole.setName(Role.RoleName.ADMIN);

        User user = new User();
        user.setId(1L);
        user.setEmail(req.getEmail());
        user.setRoles(Set.of(adminRole));

        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(eq(req.getEmail()), anyList())).thenReturn("access-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }
}
