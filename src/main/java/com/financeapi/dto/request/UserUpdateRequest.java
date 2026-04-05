package com.financeapi.dto.request;

import com.financeapi.domain.Role.RoleName;
import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateRequest {
    private String fullName;
    private Boolean active;
    private Set<RoleName> roles;
}
