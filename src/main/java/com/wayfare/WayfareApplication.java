package com.wayfare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class WayfareApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(WayfareApplication.class, args);
    }

    /**
     * Tự động quét và nạp các biến môi trường từ file .env (nếu có) vào System properties
     * Giúp GEMINI_API_KEY, JWT_SECRET, Cloudinary... hoạt động chuẩn xác trên môi trường local.
     */
    private static void loadDotEnv() {
        File[] candidates = new File[] {
                new File(".env"),
                new File("travel_backend/.env"),
                new File("../travel_backend/.env"),
                new File("../.env")
        };

        for (File envFile : candidates) {
            if (envFile.exists() && envFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                    String line;
                    int loadedCount = 0;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String value = line.substring(eqIdx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                                loadedCount++;
                            }
                        }
                    }
                    System.out.println("[WayfareApplication] Successfully loaded " + loadedCount + " variables from: " + envFile.getAbsolutePath());
                    break;
                } catch (IOException e) {
                    System.err.println("[WayfareApplication] Failed reading .env file: " + e.getMessage());
                }
            }
        }
    }
}

