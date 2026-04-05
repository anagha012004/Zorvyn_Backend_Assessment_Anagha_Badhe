package com.financeapi.security;

import com.financeapi.domain.User;
import com.financeapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (!user.isActive())
            throw new UsernameNotFoundException("User is inactive: " + email);

        var authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName().name()))
                .collect(Collectors.toList());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPasswordHash(), authorities);
    }
}
