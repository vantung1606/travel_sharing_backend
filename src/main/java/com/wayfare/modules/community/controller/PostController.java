package com.wayfare.modules.community.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.community.dto.*;
import com.wayfare.modules.community.service.CommunityRealtimeService;
import com.wayfare.modules.community.service.PostService;
import com.wayfare.modules.itinerary.dto.ItineraryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PostController {

    private final PostService postService;
    private final CommunityRealtimeService communityRealtimeService;

    private String extractViewerEmail(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return null;
    }

    private String requireAuthEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để thực hiện thao tác này!");
        }
        return authentication.getName();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PostDto>>> getCommunityPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        String effectiveEmail = extractViewerEmail(authentication);
        log.info("REST request to get community posts: category={}, keyword={}, email={}", category, keyword, effectiveEmail);
        List<PostDto> posts = postService.getCommunityPosts(category, keyword, effectiveEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bài viết thành công", posts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDto>> getPostById(
            @PathVariable Long id,
            Authentication authentication) {
        String effectiveEmail = extractViewerEmail(authentication);
        log.info("REST request to get post detail: id={}, viewer={}", id, effectiveEmail);
        PostDto post = postService.getPostById(id, effectiveEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết bài viết thành công", post));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostDto>> createPost(
            @Valid @RequestBody CreatePostRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to create community post by {}: {}", email, request.getTitle());
        PostDto created = postService.createPost(request, email, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Đăng bài viết mới thành công!", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDto>> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to update community post #{}: email={}", id, email);
        PostDto updated = postService.updatePost(id, request, email, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bài viết thành công!", updated));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<ApiResponse<PostDto>> updateVisibility(
            @PathVariable Long id,
            @RequestParam String visibility,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to update visibility for post #{}: visibility={}, email={}", id, visibility, email);
        PostDto updated = postService.updateVisibility(id, visibility, email);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật chế độ hiển thị bài viết!", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long id,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to delete community post #{}: email={}", id, email);
        postService.deletePost(id, email);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bài viết thành công!", null));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(
            @PathVariable Long id,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to toggle like on post {}: email={}", id, email);
        Map<String, Object> result = postService.toggleLikePost(id, email);
        try {
            communityRealtimeService.publishLikeChanged(id, email, result.get("likeCount"));
        } catch (Exception e) {
            log.error("Failed to broadcast realtime like event for post {}: {}", id, e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Cập nhật lượt thích thành công", result));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleBookmark(
            @PathVariable Long id,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to toggle bookmark on post {}: email={}", id, email);
        Map<String, Object> result = postService.toggleBookmarkPost(id, email);
        return ResponseEntity.ok(ApiResponse.success(
                (Boolean) result.get("isBookmarked") ? "Đã lưu bài viết vào Bộ sưu tập cá nhân" : "Đã bỏ lưu bài viết khỏi bộ sưu tập",
                result
        ));
    }

    @GetMapping("/bookmarked/ids")
    public ResponseEntity<ApiResponse<List<Long>>> getBookmarkedPostIds(
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to get bookmarked post IDs for email={}", email);
        List<Long> ids = postService.getBookmarkedPostIds(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ID bài viết đã lưu thành công", ids));
    }

    @GetMapping("/bookmarked")
    public ResponseEntity<ApiResponse<List<PostDto>>> getBookmarkedPosts(
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to get bookmarked posts for email={}", email);
        List<PostDto> posts = postService.getBookmarkedPosts(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bài viết đã lưu thành công", posts));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentDto>>> getComments(@PathVariable Long id) {
        log.info("REST request to get comments for post {}", id);
        List<CommentDto> comments = postService.getPostComments(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy bình luận thành công", comments));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<CommentDto>> addComment(
            @PathVariable Long id,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to add comment on post {} by {}", id, email);
        CommentDto created = postService.addComment(id, request, email);
        try {
            communityRealtimeService.publishCommentAdded(
                    id, created.getId(), request.getParentId(), email, postService.getCommentCount(id));
        } catch (Exception e) {
            log.error("Failed to broadcast realtime comment event for post {}: {}", id, e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Đăng bình luận thành công!", created));
    }

    @PostMapping("/{id}/clone-itinerary")
    public ResponseEntity<ApiResponse<ItineraryDto>> cloneItinerary(
            @PathVariable Long id,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to clone itinerary from post {} by {}", id, email);
        ItineraryDto cloned = postService.cloneItineraryFromPost(id, email);
        return ResponseEntity.ok(ApiResponse.success("Đã sao chép chuyến đi thành công vào 'Lịch trình của tôi'!", cloned));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to delete comment {} from post {} by {}", commentId, id, email);
        postService.deleteComment(id, commentId, email);
        try {
            communityRealtimeService.publishCommentDeleted(id, commentId, email, postService.getCommentCount(id));
        } catch (Exception e) {
            log.error("Failed to broadcast realtime comment-delete event for post {}: {}", id, e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Đã xóa bình luận thành công!", null));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reportPost(
            @PathVariable Long id,
            @Valid @RequestBody ReportPostRequest request,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to report post {} by {}: reason={}", id, email, request.getReason());
        Map<String, Object> result = postService.reportPost(id, request, email);
        return ResponseEntity.ok(ApiResponse.success("Báo cáo vi phạm đã được ghi nhận thành công!", result));
    }
}
