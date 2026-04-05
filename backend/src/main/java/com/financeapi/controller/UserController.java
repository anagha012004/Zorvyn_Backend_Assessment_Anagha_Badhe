package com.financeapi.controller;

import com.financeapi.dto.request.UserUpdateRequest;
import com.financeapi.dto.response.RiskProfileResponse;
import com.financeapi.dto.response.UserResponse;
import com.financeapi.service.UserService;
import com.financeapi.service.impl.VelocityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management (Admin only)")
public class UserController {

    private final UserService userService;
    private final VelocityService velocityService;

    @GetMapping
    @Operation(summary = "List all users")
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user name, status, or roles")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
            @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a user (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/risk-profile")
    @Operation(summary = "Behavioral velocity risk profile for a user (ADMIN only)")
    public ResponseEntity<RiskProfileResponse> getRiskProfile(@PathVariable Long id) {
        return ResponseEntity.ok(velocityService.getRiskProfile(id));
    }
}
