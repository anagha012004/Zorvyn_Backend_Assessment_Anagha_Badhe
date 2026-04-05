package com.financeapi.dto.response;

import com.financeapi.domain.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private boolean active;
    private Set<String> roles;
    private LocalDateTime createdAt;

    public static UserResponse from(User u) {
        UserResponse r = new UserResponse();
        r.id = u.getId();
        r.email = u.getEmail();
        r.fullName = u.getFullName();
        r.active = u.isActive();
        r.roles = u.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toSet());
        r.createdAt = u.getCreatedAt();
        return r;
    }
}
