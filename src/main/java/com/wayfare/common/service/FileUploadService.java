package com.wayfare.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FileUploadService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder:wayfare/media}")
    private String cloudinaryFolder;

    @Autowired
    public FileUploadService(@Autowired(required = false) Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
        if (this.cloudinary != null) {
            log.info("FileUploadService initialized with 100% Cloudinary Cloud Storage active.");
        } else {
            log.warn("FileUploadService: Cloudinary bean is not active. Media uploads will require Cloudinary configuration.");
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

        if (cloudinary == null) {
            log.error("Cloudinary is not configured. Direct upload rejected to prevent storing files on local disk.");
            throw new IllegalStateException("Hệ thống lưu trữ đám mây Cloudinary chưa sẵn sàng. Vui lòng kiểm tra lại cấu hình Cloudinary trên máy chủ!");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "media";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        boolean isVideo = contentType.startsWith("video/") ||
                originalName.toLowerCase().endsWith(".mp4") ||
                originalName.toLowerCase().endsWith(".mov") ||
                originalName.toLowerCase().endsWith(".webm") ||
                originalName.toLowerCase().endsWith(".avi");

        try {
            log.info("Uploading {} to Cloudinary Cloud CDN (resource_type: {})...", originalName, isVideo ? "video" : "auto");
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
            throw new RuntimeException("Cloudinary không trả về đường dẫn secure_url hợp lệ");
        } catch (Exception e) {
            log.error("Cloudinary upload failed for {}: {}", originalName, e.getMessage(), e);
            throw new RuntimeException("Tải tệp tin lên Cloudinary thất bại: " + e.getMessage());
        }
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
            String url = uploadSingleFile(file, request);
            if (url != null) {
                fileUrls.add(url);
            }
        }

        return fileUrls;
    }
}
