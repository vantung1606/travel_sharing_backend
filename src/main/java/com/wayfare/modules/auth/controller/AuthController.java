package com.wayfare.modules.auth.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.modules.auth.dto.AuthResponse;
import com.wayfare.modules.auth.dto.GoogleAuthRequest;
import com.wayfare.modules.auth.dto.LoginRequest;
import com.wayfare.modules.auth.dto.RegisterRequest;
import com.wayfare.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

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

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleAuthRequest request, HttpServletRequest httpRequest) {
        log.info("REST request to authenticate with Google OAuth token");
        AuthResponse response = authService.loginWithGoogle(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập bằng tài khoản Google thành công! 🎉", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestParam(required = false) String email,
            org.springframework.security.core.Authentication authentication,
            HttpServletRequest httpRequest) {
        String authEmail = (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName()))
                ? authentication.getName() : email;
        log.info("REST request to logout user with email: {}", authEmail);
        authService.logout(authEmail, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công!", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> forgotPassword(
            @Valid @RequestBody com.wayfare.modules.auth.dto.ForgotPasswordRequest request) {
        log.info("REST request forgot password for email: {}", request.getEmail());
        java.util.Map<String, Object> result = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Mã xác thực OTP đã được gửi đến email của bạn!", result));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> verifyOtp(
            @Valid @RequestBody com.wayfare.modules.auth.dto.VerifyOtpRequest request) {
        log.info("REST request verify OTP for email: {}", request.getEmail());
        java.util.Map<String, Object> result = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Xác thực mã OTP thành công!", result));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody com.wayfare.modules.auth.dto.ResetPasswordRequest request) {
        log.info("REST request reset password for email: {}", request.getEmail());
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới.", null));
    }
}


