package com.wayfare.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
    // Chạy ở Railway (có Volume gắn vào) → lưu vào ổ đĩa bền; chạy local → thư mục uploads/posts như cũ
    private final Path rootUploadPath = resolveUploadRoot();

    private static Path resolveUploadRoot() {
        String mountPath = System.getenv("RAILWAY_VOLUME_MOUNT_PATH");
        if (mountPath != null && !mountPath.isBlank()) {
            return Paths.get(mountPath, "uploads", "posts");
        }
        return Paths.get("uploads/posts");
    }

    @Value("${cloudinary.folder:wayfare/media}")
    private String cloudinaryFolder;

    @Autowired
    public FileUploadService(@Autowired(required = false) Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
        try {
            if (!Files.exists(rootUploadPath)) {
                Files.createDirectories(rootUploadPath);
                log.info("Initialized local upload directory: {}", rootUploadPath.toAbsolutePath());
            }
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
        return uploadToLocalDisk(file, originalName);
    }

    public List<String> uploadMultipleFiles(MultipartFile[] files) {
        List<String> fileUrls = new ArrayList<>();
        if (files == null || files.length == 0) {
            return fileUrls;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            String url = uploadSingleFile(file);
            if (url != null) {
                fileUrls.add(url);
            }
        }

        return fileUrls;
    }

    private String uploadToLocalDisk(MultipartFile file, String originalName) {
        String extension = "";
        if (originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        } else {
            extension = ".jpg";
        }

        String uniqueName = UUID.randomUUID() + extension;

        try {
            Path targetLocation = rootUploadPath.resolve(uniqueName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // URL công khai phải trỏ đúng nơi phục vụ ảnh: Railway dùng domain thật, local dùng localhost:8081
            String publicDomain = System.getenv("RAILWAY_PUBLIC_DOMAIN");
            String baseUrl = (publicDomain != null && !publicDomain.isBlank())
                    ? "https://" + publicDomain
                    : "http://localhost:8081";
            String publicUrl = baseUrl + "/uploads/posts/" + uniqueName;
            log.info("Saved file locally to {}", publicUrl);
            return publicUrl;
        } catch (IOException e) {
            log.error("Failed to store file locally {}: {}", originalName, e.getMessage());
            throw new RuntimeException("Lỗi lưu trữ tệp tin cục bộ: " + originalName);
        }
    }
}
