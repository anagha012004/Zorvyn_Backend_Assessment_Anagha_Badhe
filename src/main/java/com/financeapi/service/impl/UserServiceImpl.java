package com.financeapi.service.impl;

import com.financeapi.domain.Role;
import com.financeapi.domain.User;
import com.financeapi.dto.request.UserUpdateRequest;
import com.financeapi.dto.response.UserResponse;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.RoleRepository;
import com.financeapi.repository.UserRepository;
import com.financeapi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).collect(Collectors.toList());
    }

    @Override
    public UserResponse getUserById(Long id) {
        return UserResponse.from(getUser(id));
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = getUser(id);
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getActive() != null) user.setActive(request.getActive());
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> roles = request.getRoles().stream()
                    .map(name -> roleRepository.findByName(name)
                            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + name)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = getUser(id);
        user.setActive(false);
        userRepository.save(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
