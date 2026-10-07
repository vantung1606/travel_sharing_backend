package com.wayfare.common.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.common.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/multiple")
    public ResponseEntity<ApiResponse<List<String>>> uploadMultiple(
            @RequestParam("files") MultipartFile[] files
    ) {
        log.info("Received request to upload {} file(s)", files != null ? files.length : 0);
        List<String> urls = fileUploadService.uploadMultipleFiles(files);
        return ResponseEntity.ok(ApiResponse.success("Tải tệp tin lên máy chủ thành công!", urls));
    }
}


