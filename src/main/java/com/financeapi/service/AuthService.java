package com.financeapi.service;

import com.financeapi.dto.request.LoginRequest;
import com.financeapi.dto.request.RefreshTokenRequest;
import com.financeapi.dto.request.RegisterRequest;
import com.financeapi.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
    void logout(String refreshToken);
}
