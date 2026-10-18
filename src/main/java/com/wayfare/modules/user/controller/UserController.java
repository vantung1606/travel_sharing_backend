package com.wayfare.modules.user.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.user.dto.FollowUserDto;
import com.wayfare.modules.user.dto.UpdateProfileRequest;
import com.wayfare.modules.user.dto.UserProfileDto;
import com.wayfare.modules.user.service.UserFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class UserController {

    private final UserFollowService userFollowService;

    private String extractViewerEmail(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return null;
    }

    private String requireAuthEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        return authentication.getName();
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserProfile(
            @PathVariable Long id,
            Authentication authentication) {
        String viewerEmail = extractViewerEmail(authentication);
        log.info("REST request to get profile of user ID {} by viewer {}", id, viewerEmail);
        UserProfileDto profile = userFollowService.getUserProfile(id, viewerEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", profile));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFollow(
            @PathVariable Long id,
            Authentication authentication) {
        String currentUserEmail = requireAuthEmail(authentication);
        log.info("REST request to toggle follow on user ID {} by {}", id, currentUserEmail);
        Map<String, Object> result = userFollowService.toggleFollow(id, currentUserEmail);
        String msg = Boolean.TRUE.equals(result.get("isFollowing"))
                ? "Đã theo dõi người dùng thành công!"
                : "Đã hủy theo dõi người dùng!";
        return ResponseEntity.ok(ApiResponse.success(msg, result));
    }

    @GetMapping("/following/ids")
    public ResponseEntity<ApiResponse<List<Long>>> getFollowingUserIds(
            Authentication authentication) {
        String currentUserEmail = requireAuthEmail(authentication);
        log.info("REST request to get following user IDs for viewer {}", currentUserEmail);
        List<Long> ids = userFollowService.getFollowingUserIds(currentUserEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ID người đang theo dõi thành công", ids));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> getMyProfile(
            Authentication authentication) {
        String currentUserEmail = requireAuthEmail(authentication);
        log.info("REST request to get profile of current user by email {}", currentUserEmail);
        com.wayfare.entity.User current = userFollowService.resolveUser(currentUserEmail);
        UserProfileDto profile = userFollowService.getUserProfile(current.getId(), currentUserEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> updateProfile(
            @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        String currentUserEmail = requireAuthEmail(authentication);
        log.info("REST request to update profile by authenticated user {}", currentUserEmail);
        UserProfileDto updated = userFollowService.updateUserProfile(null, request, currentUserEmail);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ cá nhân thành công!", updated));
    }

    @GetMapping("/following")
    public ResponseEntity<ApiResponse<List<FollowUserDto>>> getMyFollowingList(
            Authentication authentication) {
        String currentUserEmail = requireAuthEmail(authentication);
        log.info("REST request to get following list for current user email {}", currentUserEmail);
        com.wayfare.entity.User current = userFollowService.resolveUser(currentUserEmail);
        List<FollowUserDto> list = userFollowService.getFollowingList(current.getId(), currentUserEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người đang theo dõi thành công", list));
    }

    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<List<FollowUserDto>>> getMyFollowersList(
            Authentication authentication) {
        String currentUserEmail = requireAuthEmail(authentication);
        log.info("REST request to get followers list for current user email {}", currentUserEmail);
        com.wayfare.entity.User current = userFollowService.resolveUser(currentUserEmail);
        List<FollowUserDto> list = userFollowService.getFollowersList(current.getId(), currentUserEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người theo dõi thành công", list));
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<ApiResponse<List<FollowUserDto>>> getFollowingList(
            @PathVariable Long id,
            Authentication authentication) {
        String viewerEmail = extractViewerEmail(authentication);
        log.info("REST request to get following list of user ID {} by viewer {}", id, viewerEmail);
        List<FollowUserDto> list = userFollowService.getFollowingList(id, viewerEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người đang theo dõi thành công", list));
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<ApiResponse<List<FollowUserDto>>> getFollowersList(
            @PathVariable Long id,
            Authentication authentication) {
        String viewerEmail = extractViewerEmail(authentication);
        log.info("REST request to get followers list of user ID {} by viewer {}", id, viewerEmail);
        List<FollowUserDto> list = userFollowService.getFollowersList(id, viewerEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người theo dõi thành công", list));
    }
}
