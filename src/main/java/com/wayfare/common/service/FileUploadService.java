package com.wayfare.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {

    private final Cloudinary cloudinary;
    private final Path rootUploadPath;

    @Value("${cloudinary.folder:wayfare/media}")
    private String cloudinaryFolder;

    private static Path resolveUploadRoot() {
        String mountPath = System.getenv("RAILWAY_VOLUME_MOUNT_PATH");
        if (mountPath != null && !mountPath.isBlank()) {
            try {
                Path p = Paths.get(mountPath, "uploads", "posts");
                Files.createDirectories(p);
                return p;
            } catch (Exception e) {
                log.warn("Could not create RAILWAY_VOLUME_MOUNT_PATH directory: {}. Falling back to default.", e.getMessage());
            }
        }
        try {
            Path defaultPath = Paths.get("uploads", "posts");
            Files.createDirectories(defaultPath);
            return defaultPath;
        } catch (Exception e) {
            Path tmpPath = Paths.get(System.getProperty("java.io.tmpdir"), "wayfare", "uploads", "posts");
            try {
                Files.createDirectories(tmpPath);
            } catch (Exception ignored) {}
            return tmpPath;
        }
    }

    @Autowired
    public FileUploadService(@Autowired(required = false) Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
        this.rootUploadPath = resolveUploadRoot();
        try {
            if (!Files.exists(rootUploadPath)) {
                Files.createDirectories(rootUploadPath);
            }
            log.info("Initialized local upload directory: {}", rootUploadPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Could not initialize local upload folder: {}", e.getMessage());
        }

        if (this.cloudinary != null) {
            log.info("FileUploadService initialized with Cloudinary Cloud Storage active.");
        } else {
            log.info("FileUploadService initialized with Local Disk fallback storage.");
        }
    }

    public boolean isCloudinaryActive() {
        return this.cloudinary != null;
    }

    public String uploadSingleFile(MultipartFile file) {
        return uploadSingleFile(file, null);
    }

    public String uploadSingleFile(MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "media";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        boolean isVideo = contentType.startsWith("video/") ||
                originalName.toLowerCase().endsWith(".mp4") ||
                originalName.toLowerCase().endsWith(".mov") ||
                originalName.toLowerCase().endsWith(".webm") ||
                originalName.toLowerCase().endsWith(".avi");

        // 1. Prioritize Cloudinary Cloud Media Upload
        if (cloudinary != null) {
            try {
                log.info("Uploading {} to Cloudinary (resource_type: {})...", originalName, isVideo ? "video" : "auto");
                Map<String, Object> params = ObjectUtils.asMap(
                        "folder", cloudinaryFolder,
                        "resource_type", isVideo ? "video" : "auto"
                );

                @SuppressWarnings("rawtypes")
                Map uploadResult = cloudinary.uploader().upload(file.getBytes(), params);
                String secureUrl = (String) uploadResult.get("secure_url");
                if (secureUrl != null && !secureUrl.isBlank()) {
                    log.info("Successfully uploaded {} to Cloudinary CDN: {}", originalName, secureUrl);
                    return secureUrl;
                }
            } catch (Exception e) {
                log.error("Cloudinary upload failed for {}: {}. Falling back to local disk storage.", originalName, e.getMessage());
            }
        }

        // 2. Safe Fallback to Local Disk
        return uploadToLocalDisk(file, originalName, request);
    }

    public List<String> uploadMultipleFiles(MultipartFile[] files) {
        return uploadMultipleFiles(files, null);
    }

    public List<String> uploadMultipleFiles(MultipartFile[] files, HttpServletRequest request) {
        List<String> fileUrls = new ArrayList<>();
        if (files == null || files.length == 0) {
            return fileUrls;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                String url = uploadSingleFile(file, request);
                if (url != null) {
                    fileUrls.add(url);
                }
            } catch (Exception e) {
                log.error("Error uploading single item in multiple files: {}", e.getMessage());
            }
        }

        return fileUrls;
    }

    public String determineBaseUrl(HttpServletRequest request) {
        HttpServletRequest currentReq = request;
        if (currentReq == null) {
            try {
                RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
                if (attrs instanceof ServletRequestAttributes) {
                    currentReq = ((ServletRequestAttributes) attrs).getRequest();
                }
            } catch (Exception ignored) {}
        }

        if (currentReq != null) {
            String proto = currentReq.getHeader("X-Forwarded-Proto");
            String host = currentReq.getHeader("X-Forwarded-Host");
            if (host == null || host.isBlank()) {
                host = currentReq.getHeader("Host");
            }
            if (host != null && !host.isBlank() && !host.contains("localhost")) {
                String scheme = (proto != null && !proto.isBlank()) ? proto : currentReq.getScheme();
                return scheme + "://" + host;
            }
        }

        String publicDomain = System.getenv("RAILWAY_PUBLIC_DOMAIN");
        if (publicDomain != null && !publicDomain.isBlank()) {
            return publicDomain.startsWith("http") ? publicDomain : "https://" + publicDomain;
        }

        String staticDomain = System.getenv("RAILWAY_STATIC_URL");
        if (staticDomain != null && !staticDomain.isBlank()) {
            return staticDomain.startsWith("http") ? staticDomain : "https://" + staticDomain;
        }

        if (currentReq != null && currentReq.getHeader("Host") != null) {
            return currentReq.getScheme() + "://" + currentReq.getHeader("Host");
        }

        return "http://localhost:8081";
    }

    private String uploadToLocalDisk(MultipartFile file, String originalName, HttpServletRequest request) {
        String extension = "";
        if (originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        } else {
            extension = ".jpg";
        }

        String uniqueName = UUID.randomUUID() + extension;

        try {
            Path targetLocation = rootUploadPath.resolve(uniqueName);
            if (targetLocation.getParent() != null && !Files.exists(targetLocation.getParent())) {
                Files.createDirectories(targetLocation.getParent());
            }
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String baseUrl = determineBaseUrl(request);
            String publicUrl = baseUrl + "/uploads/posts/" + uniqueName;
            log.info("Saved file locally to {}", publicUrl);
            return publicUrl;
        } catch (IOException e) {
            log.error("Failed to store file locally in primary path {}: {}. Attempting fallback...", originalName, e.getMessage());
            try {
                Path fallbackDir = Paths.get(System.getProperty("java.io.tmpdir"), "wayfare", "uploads", "posts");
                Files.createDirectories(fallbackDir);
                Path fallbackTarget = fallbackDir.resolve(uniqueName);
                Files.copy(file.getInputStream(), fallbackTarget, StandardCopyOption.REPLACE_EXISTING);

                String baseUrl = determineBaseUrl(request);
                String publicUrl = baseUrl + "/uploads/posts/" + uniqueName;
                log.info("Saved file locally via fallback to {}", publicUrl);
                return publicUrl;
            } catch (IOException ex) {
                log.error("Failed to store file in fallback location {}: {}", originalName, ex.getMessage());
                throw new RuntimeException("Lỗi lưu trữ tệp tin cục bộ: " + originalName);
            }
        }
    }
}
