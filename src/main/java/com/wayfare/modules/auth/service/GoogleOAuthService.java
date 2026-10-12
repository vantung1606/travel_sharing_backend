package com.wayfare.modules.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    @Builder
    public static class GoogleUserInfo {
        private String email;
        private String name;
        private String picture;
        private String sub;
        private boolean emailVerified;
    }

    /**
     * Xác thực Token Google với máy chủ xác thực chính thức của Google (OAuth 2.0).
     * Hỗ trợ xác thực cả Google ID Token (JWT) và OAuth2 Access Token.
     */
    public GoogleUserInfo verifyGoogleToken(String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Google Token không được để trống!");
        }

        log.info("Verifying Google token with Google OAuth servers...");

        // 1. Thử xác thực dưới dạng Google ID Token qua endpoint tokeninfo
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + token.trim();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                if (json.has("email")) {
                    String email = json.get("email").asText();
                    String name = json.has("name") ? json.get("name").asText() : email.split("@")[0];
                    String picture = json.has("picture") ? json.get("picture").asText() : null;
                    String sub = json.has("sub") ? json.get("sub").asText() : "";
                    boolean verified = !json.has("email_verified") || json.get("email_verified").asBoolean(true);

                    log.info("Google ID Token verified successfully for email: {}", email);
                    return GoogleUserInfo.builder()
                            .email(email)
                            .name(name)
                            .picture(picture)
                            .sub(sub)
                            .emailVerified(verified)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Google ID Token verification attempt failed: {}. Trying Userinfo endpoint...", e.getMessage());
        }

        // 2. Thử xác thực dưới dạng Google Access Token qua endpoint userinfo
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token.trim());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                if (json.has("email")) {
                    String email = json.get("email").asText();
                    String name = json.has("name") ? json.get("name").asText() : email.split("@")[0];
                    String picture = json.has("picture") ? json.get("picture").asText() : null;
                    String sub = json.has("sub") ? json.get("sub").asText() : "";
                    boolean verified = !json.has("email_verified") || json.get("email_verified").asBoolean(true);

                    log.info("Google Access Token verified successfully for email: {}", email);
                    return GoogleUserInfo.builder()
                            .email(email)
                            .name(name)
                            .picture(picture)
                            .sub(sub)
                            .emailVerified(verified)
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Google Access Token verification failed: {}", e.getMessage());
        }

        log.error("Failed to verify Google token with both ID Token and Access Token endpoints");
        throw new RuntimeException("Xác thực tài khoản Google thất bại: Token không hợp lệ hoặc đã hết hạn!");
    }
}
