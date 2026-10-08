package com.wayfare.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        if (cloudName != null && !cloudName.isBlank() &&
            apiKey != null && !apiKey.isBlank() &&
            apiSecret != null && !apiSecret.isBlank()) {
            
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", cloudName.trim());
            config.put("api_key", apiKey.trim());
            config.put("api_secret", apiSecret.trim());
            config.put("secure", true);

            log.info("Cloudinary storage successfully initialized with cloud_name: {}", cloudName);
            return new Cloudinary(config);
        } else {
            log.info("Cloudinary credentials are not configured. FileUploadService will use local storage fallback.");
            return null;
        }
    }
}
