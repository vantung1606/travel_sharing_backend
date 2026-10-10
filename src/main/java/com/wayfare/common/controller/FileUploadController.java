package com.wayfare.common.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.common.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStorageStatus() {
        log.info("REST request to check file storage service status");
        boolean isCloudinary = fileUploadService.isCloudinaryActive();
        Map<String, Object> status = new HashMap<>();
        status.put("provider", isCloudinary ? "CLOUDINARY" : "LOCAL_DISK");
        status.put("isCloudinaryActive", isCloudinary);
        status.put("cdnEnabled", isCloudinary);
        status.put("description", isCloudinary
                ? "Dịch vụ Cloudinary CDN đang hoạt động cho lưu trữ và phân phối đa phương tiện."
                : "Hệ thống đang hoạt động ở chế độ lưu trữ cục bộ dự phòng (Local disk fallback).");

        return ResponseEntity.ok(ApiResponse.success("Kiểm tra trạng thái lưu trữ thành công", status));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> uploadSingle(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("Received request to upload single file: {}", file != null ? file.getOriginalFilename() : "null");
        String url = fileUploadService.uploadSingleFile(file);
        return ResponseEntity.ok(ApiResponse.success("Tải tệp tin lên thành công!", url));
    }

    @PostMapping("/multiple")
    public ResponseEntity<ApiResponse<List<String>>> uploadMultiple(
            @RequestParam("files") MultipartFile[] files
    ) {
        log.info("Received request to upload {} file(s)", files != null ? files.length : 0);
        List<String> urls = fileUploadService.uploadMultipleFiles(files);
        return ResponseEntity.ok(ApiResponse.success("Tải tệp tin lên máy chủ thành công!", urls));
    }
}
