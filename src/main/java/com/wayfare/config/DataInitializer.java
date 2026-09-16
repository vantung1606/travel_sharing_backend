package com.wayfare.config;

import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import com.wayfare.repository.RoleRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking sample data initialization...");

        // Ensure Roles exist
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_ADMIN")
                        .description("Quản trị viên hệ thống")
                        .build()));

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("Người dùng tiêu chuẩn")
                        .build()));

        // Create Sample Admin User: admin@gmail.com / admin123
        if (!userRepository.existsByEmail("admin@gmail.com")) {
            User adminUser = User.builder()
                    .email("admin@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("Quản Trị Viên (Admin)")
                    .handle("@admin_wayfare")
                    .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                    .bio("Quản trị viên hệ thống Wayfare Platform 🛡️")
                    .isVerified(true)
                    .roles(Set.of(adminRole, userRole))
                    .build();

            userRepository.save(adminUser);
            log.info(">>> SUCCESS: Created Sample Admin Account -> Email: admin@gmail.com | Password: admin123");
        } else {
            log.info(">>> Sample Admin Account (admin@gmail.com) already exists.");
        }
    }
}
