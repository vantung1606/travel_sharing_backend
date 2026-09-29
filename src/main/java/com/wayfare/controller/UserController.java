package com.wayfare.controller;

import com.wayfare.dto.ApiResponse;
import com.wayfare.dto.UserProfileDto;
import com.wayfare.service.UserFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class UserController {

    private final UserFollowService userFollowService;

    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserProfile(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to get profile of user ID {} by viewer {}", id, email);
        UserProfileDto profile = userFollowService.getUserProfile(id, email);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", profile));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFollow(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to toggle follow on user ID {} by {}", id, email);
        Map<String, Object> result = userFollowService.toggleFollow(id, email);
        String msg = Boolean.TRUE.equals(result.get("isFollowing"))
                ? "Đã theo dõi người dùng thành công!"
                : "Đã hủy theo dõi người dùng!";
        return ResponseEntity.ok(ApiResponse.success(msg, result));
    }
}
