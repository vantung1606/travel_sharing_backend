package com.wayfare.service;

import com.wayfare.dto.AuthResponse;
import com.wayfare.dto.LoginRequest;
import com.wayfare.dto.RegisterRequest;
import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import com.wayfare.repository.RoleRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Starting user registration for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed. Email already exists: {}", request.getEmail());
            throw new RuntimeException("Email này đã được đăng ký tài khoản trên hệ thống!");
        }

        // Fetch or create ROLE_USER
        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("Quyền người dùng tiêu chuẩn")
                        .build()));

        // Generate unique handle
        String baseHandle = "@" + request.getEmail().split("@")[0].toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");
        String handle = baseHandle;
        int count = 1;
        while (userRepository.existsByHandle(handle)) {
            handle = baseHandle + count++;
        }

        User newUser = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .handle(handle)
                .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .isVerified(true)
                .roles(Set.of(defaultRole))
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("User registered successfully. Assigned ID: {}, Handle: {}", savedUser.getId(), savedUser.getHandle());

        String token = "jwt-access-token-" + UUID.randomUUID();

        return AuthResponse.builder()
                .token(token)
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .handle(savedUser.getHandle())
                .avatar(savedUser.getAvatarUrl())
                .roles(savedUser.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed. Email not found: {}", request.getEmail());
                    return new RuntimeException("Email hoặc mật khẩu không chính xác!");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed. Password mismatch for email: {}", request.getEmail());
            throw new RuntimeException("Email hoặc mật khẩu không chính xác!");
        }

        log.info("Login successful for user ID: {}, email: {}", user.getId(), user.getEmail());

        String token = "jwt-access-token-" + UUID.randomUUID();

        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .handle(user.getHandle())
                .avatar(user.getAvatarUrl() != null ? user.getAvatarUrl() : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();
    }
}
