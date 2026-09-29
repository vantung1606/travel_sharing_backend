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

    @GetMapping("/following/ids")
    public ResponseEntity<ApiResponse<java.util.List<Long>>> getFollowingUserIds(
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to get following user IDs for viewer {}", email);
        java.util.List<Long> ids = userFollowService.getFollowingUserIds(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ID người đang theo dõi thành công", ids));
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<ApiResponse<java.util.List<com.wayfare.dto.FollowUserDto>>> getFollowingList(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to get following list of user ID {} by viewer {}", id, email);
        java.util.List<com.wayfare.dto.FollowUserDto> list = userFollowService.getFollowingList(id, email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người đang theo dõi thành công", list));
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<ApiResponse<java.util.List<com.wayfare.dto.FollowUserDto>>> getFollowersList(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to get followers list of user ID {} by viewer {}", id, email);
        java.util.List<com.wayfare.dto.FollowUserDto> list = userFollowService.getFollowersList(id, email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người theo dõi thành công", list));
    }
}
