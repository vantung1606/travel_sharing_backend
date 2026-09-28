package com.wayfare.controller;

import com.wayfare.dto.ApiResponse;
import com.wayfare.dto.AuthResponse;
import com.wayfare.dto.LoginRequest;
import com.wayfare.dto.RegisterRequest;
import com.wayfare.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        log.info("REST request to register new user with email: {}", request.getEmail());
        AuthResponse response = authService.register(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Đăng ký tài khoản thành công!", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("REST request to login user with email: {}", request.getEmail());
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công!", response));
    }
}
