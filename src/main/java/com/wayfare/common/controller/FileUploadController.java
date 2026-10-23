package com.wayfare.common.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.common.service.FileUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
@Slf4j
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

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
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            HttpServletRequest request
    ) {
        MultipartFile target = (file != null && !file.isEmpty()) ? file : null;
        if (target == null && files != null && files.length > 0) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    target = f;
                    break;
                }
            }
        }

        if (target == null || target.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn một tệp tin hợp lệ để tải lên"));
        }

        log.info("Received request to upload single file: {}", target.getOriginalFilename());
        String url = fileUploadService.uploadSingleFile(target, request);
        return ResponseEntity.ok(ApiResponse.success("Tải tệp tin lên thành công!", url));
    }

    @PostMapping("/multiple")
    public ResponseEntity<ApiResponse<List<String>>> uploadMultiple(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "file", required = false) MultipartFile[] singleFiles,
            @RequestParam(value = "files[]", required = false) MultipartFile[] arrayFiles,
            HttpServletRequest request
    ) {
        List<MultipartFile> allFiles = new ArrayList<>();
        if (files != null) {
            allFiles.addAll(Arrays.asList(files));
        }
        if (singleFiles != null) {
            allFiles.addAll(Arrays.asList(singleFiles));
        }
        if (arrayFiles != null) {
            allFiles.addAll(Arrays.asList(arrayFiles));
        }

        List<MultipartFile> validFiles = allFiles.stream()
                .filter(f -> f != null && !f.isEmpty())
                .collect(Collectors.toList());

        if (validFiles.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn ít nhất một tệp tin hợp lệ để tải lên"));
        }

        log.info("Received request to upload {} valid file(s)", validFiles.size());
        List<String> urls = fileUploadService.uploadMultipleFiles(validFiles.toArray(new MultipartFile[0]), request);
        return ResponseEntity.ok(ApiResponse.success("Tải tệp tin lên máy chủ thành công!", urls));
    }
}
