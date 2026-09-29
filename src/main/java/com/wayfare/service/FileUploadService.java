package com.wayfare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {

    private final Path rootUploadPath = Paths.get("uploads/posts");

    public FileUploadService() {
        try {
            if (!Files.exists(rootUploadPath)) {
                Files.createDirectories(rootUploadPath);
                log.info("Initialized upload directory: {}", rootUploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Could not initialize upload folder: {}", e.getMessage());
        }
    }

    public List<String> uploadMultipleFiles(MultipartFile[] files) {
        List<String> fileUrls = new ArrayList<>();
        if (files == null || files.length == 0) {
            return fileUrls;
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
            } else {
                extension = ".jpg";
            }

            String uniqueName = UUID.randomUUID() + extension;

            try {
                Path targetLocation = rootUploadPath.resolve(uniqueName);
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

                String publicUrl = "http://localhost:8081/uploads/posts/" + uniqueName;
                fileUrls.add(publicUrl);
                log.info("Saved uploaded file {} to {}", originalName, publicUrl);
            } catch (IOException e) {
                log.error("Failed to store file {}: {}", originalName, e.getMessage());
                throw new RuntimeException("Lỗi lưu trữ tệp tin: " + originalName);
            }
        }

        return fileUrls;
    }
}
