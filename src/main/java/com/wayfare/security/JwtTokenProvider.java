package com.wayfare.security;

import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@Slf4j
public class JwtTokenProvider {

    private final Key signingKey;
    private final long jwtExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration:86400000}") long jwtExpirationMs) {
        this.jwtExpirationMs = jwtExpirationMs;
        boolean isProduction = System.getenv("RAILWAY_ENVIRONMENT") != null
                || System.getenv("RAILWAY_STATIC_URL") != null
                || "production".equalsIgnoreCase(System.getenv("SPRING_PROFILES_ACTIVE"));

        if (secret == null || secret.trim().isBlank()) {
            if (isProduction) {
                log.error("SECURITY ALERT: 'JWT_SECRET' is not configured in production environment variables! Generated an ephemeral HS256 key to maintain uptime without 502 crash. Please configure JWT_SECRET (>= 32 chars) in Railway variables.");
            } else {
                log.warn("SECURITY NOTICE: 'JWT_SECRET' is not set. Generating a secure temporary in-memory key for local development.");
            }
            this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        } else {
            String cleanSecret = secret.trim();
            byte[] keyBytes = cleanSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                log.warn("NOTICE: 'JWT_SECRET' is shorter than 32 bytes ({} bytes). Hashing with SHA-256 to ensure 256-bit HMAC key.", keyBytes.length);
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    keyBytes = md.digest(keyBytes);
                } catch (NoSuchAlgorithmException e) {
                    log.error("SHA-256 not available", e);
                }
            }
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        List<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).collect(Collectors.toList())
                : List.of("ROLE_USER");

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId())
                .claim("fullName", user.getFullName())
                .claim("handle", user.getHandle())
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return (List<String>) claims.get("roles");
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        Number userId = (Number) claims.get("userId");
        return userId != null ? userId.longValue() : null;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Chữ ký JWT không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Mã xác thực JWT đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Định dạng JWT không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Chuỗi JWT rỗng hoặc không hợp lệ: {}", e.getMessage());
        }
        return false;
    }
}
