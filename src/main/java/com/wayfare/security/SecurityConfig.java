package com.wayfare.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Cho phép toàn bộ preflight OPTIONS requests đi qua tự do để CORS preflight không bị chặn
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public root, healthcheck, actuator, error and static resources
                .requestMatchers("/", "/health", "/api/health", "/actuator/**", "/error", "/favicon.ico").permitAll()
                // Public auth, static files, websockets, realtime sse streams and ai proxy
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws/**", "/uploads/**", "/api/realtime/**", "/api/ai/**", "/api/upload/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/home/**").permitAll()
                // Thông báo: cho phép GET /api/notifications công khai (được xử lý an toàn theo email trong controller)
                .requestMatchers(HttpMethod.GET, "/api/notifications/**").permitAll()
                // Public read-only endpoints for visitors
                .requestMatchers(HttpMethod.GET, "/api/places/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/itineraries/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/comments/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/*/profile").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/*/following").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/*/followers").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/following/ids").permitAll()
                // Danh mục tỉnh thành công khai; dấu chân riêng (/visited) vẫn cần đăng nhập
                .requestMatchers(HttpMethod.GET, "/api/provinces").permitAll()
                // Admin & moderator operations (method-level @PreAuthorize on each controller
                // keeps sensitive areas like user management / stats restricted to ADMIN only)
                .requestMatchers("/api/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MODERATOR")
                // All other operations require authenticated token
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:}")
    private String allowedOriginsEnv;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        java.util.List<String> patterns = new java.util.ArrayList<>(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://*.up.railway.app",
            "https://travelsharingfrontend-production.up.railway.app",
            "https://travelsharingbackend-production.up.railway.app"
        ));

        if (allowedOriginsEnv != null && !allowedOriginsEnv.isBlank()) {
            for (String origin : allowedOriginsEnv.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    patterns.add(trimmed);
                }
            }
        }

        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition", "Link", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
