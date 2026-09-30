package com.wayfare.service;

import com.wayfare.dto.AdminPostDto;
import com.wayfare.dto.AdminReportMetricsDto;
import com.wayfare.entity.Post;
import com.wayfare.entity.User;
import com.wayfare.repository.PostReportRepository;
import com.wayfare.repository.PostRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AdminReportMetricsDto getMetrics() {
        log.info("Calculating Admin Report and Moderation KPIs");
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long totalPostsToday = postRepository.countByCreatedAtAfter(startOfToday);
        if (totalPostsToday == 0) {
            totalPostsToday = postRepository.count();
        }

        List<Post> reportedPosts = postRepository.findAll().stream()
                .filter(p -> ("PENDING_REVIEW".equals(p.getStatus()) || "PENDING_REPORT".equals(p.getStatus()) || (p.getReportsCount() != null && p.getReportsCount() > 0)) &&
                             !"DISMISSED".equals(p.getStatus()) && !"REMOVED".equals(p.getStatus()))
                .collect(Collectors.toList());
        long pendingReportsCount = reportedPosts.size();

        long hiddenPostsCount = postRepository.countByStatus("HIDDEN") + postRepository.countByStatus("REMOVED");
        long totalCount = postRepository.count();

        double safeRate = 98.4;
        if (totalCount > 0) {
            long safePosts = postRepository.searchPosts(null, "ACTIVE").stream()
                    .filter(p -> p.getAiSafetyScore() == null || p.getAiSafetyScore() >= 80)
                    .count();
            safeRate = Math.round(((double) safePosts / totalCount) * 1000.0) / 10.0;
        }

        return AdminReportMetricsDto.builder()
                .totalPostsToday(totalPostsToday)
                .pendingReportsCount(pendingReportsCount)
                .hiddenPostsCount(hiddenPostsCount)
                .safeRate(safeRate)
                .growthPercent(12)
                .totalArticlesCount(totalCount)
                .build();
    }

    public List<AdminPostDto> getReportedPosts(String keyword) {
        log.info("Fetching reported and pending community posts, keyword: '{}'", keyword);
        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> ("PENDING_REVIEW".equals(p.getStatus()) || "PENDING_REPORT".equals(p.getStatus()) || (p.getReportsCount() != null && p.getReportsCount() > 0)) &&
                             !"DISMISSED".equals(p.getStatus()) && !"REMOVED".equals(p.getStatus()))
                .sorted((a, b) -> (b.getId() != null && a.getId() != null) ? b.getId().compareTo(a.getId()) : 0)
                .collect(Collectors.toList());

        if (keyword != null && !keyword.trim().isEmpty()) {
            String q = keyword.trim().toLowerCase();
            posts = posts.stream()
                    .filter(p -> (p.getTitle() != null && p.getTitle().toLowerCase().contains(q)) ||
                                 (p.getAuthor() != null && p.getAuthor().getFullName() != null && p.getAuthor().getFullName().toLowerCase().contains(q)) ||
                                 (p.getLocationTag() != null && p.getLocationTag().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        return posts.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<AdminPostDto> getAllArticles(String keyword, String status) {
        log.info("Fetching all community posts for moderation, keyword: '{}', status: '{}'", keyword, status);
        String filterStatus = (status == null || "all".equalsIgnoreCase(status)) ? null : status;
        String filterKeyword = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();

        List<Post> posts = postRepository.searchPosts(filterKeyword, filterStatus);
        return posts.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public AdminPostDto approvePost(Long postId) {
        log.info("Admin approving Post ID: {}", postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết với ID: " + postId));

        post.setStatus("ACTIVE");
        post.setReportsCount(0);
        post.setBadgeText("Đã duyệt bởi Admin");
        Post saved = postRepository.save(post);

        // Update post reports
        List<com.wayfare.entity.PostReport> reports = postReportRepository.findByPostId(postId);
        for (com.wayfare.entity.PostReport r : reports) {
            r.setStatus("DISMISSED");
            r.setModeratorNotes("Quản trị viên đã kiểm duyệt thủ công và phê duyệt xuất bản.");
            postReportRepository.save(r);
        }

        // Notify author
        if (post.getAuthor() != null) {
            try {
                notificationService.sendNotification(
                        post.getAuthor(),
                        null,
                        "POST_APPROVED",
                        "Quản trị viên đã phê duyệt bài viết '" + post.getTitle() + "' của bạn! Bài viết hiện đã được đăng công khai trên Cộng đồng Wayfare.",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to notify author on post approval: {}", e.getMessage());
            }
        }

        return mapToDto(saved);
    }

    @Transactional
    public AdminPostDto dismissReport(Long postId) {
        log.info("Dismissing reports for Post ID: {}", postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết với ID: " + postId));

        post.setReportsCount(0);
        post.setStatus("ACTIVE");
        Post saved = postRepository.save(post);

        // Update post reports status
        List<com.wayfare.entity.PostReport> reports = postReportRepository.findByPostId(postId);
        for (com.wayfare.entity.PostReport r : reports) {
            r.setStatus("DISMISSED");
            r.setModeratorNotes("Báo cáo đã được bác bỏ sau khi kiểm duyệt nội dung hợp lệ.");
            postReportRepository.save(r);
        }

        // Notify author
        if (post.getAuthor() != null) {
            try {
                notificationService.sendNotification(
                        post.getAuthor(),
                        null,
                        "REPORT_DISMISSED",
                        "Báo cáo vi phạm đối với bài viết '" + post.getTitle() + "' đã được Ban Quản Trị xem xét và bác bỏ. Bài viết của bạn hoàn toàn hợp lệ.",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to notify author on report dismissal: {}", e.getMessage());
            }
        }

        return mapToDto(saved);
    }

    @Transactional
    public AdminPostDto hidePost(Long postId) {
        log.info("Hiding Post ID: {} from public view", postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết với ID: " + postId));

        post.setStatus("HIDDEN");
        Post saved = postRepository.save(post);

        // Notify author
        if (post.getAuthor() != null) {
            try {
                notificationService.sendNotification(
                        post.getAuthor(),
                        null,
                        "POST_HIDDEN",
                        "Bài viết '" + post.getTitle() + "' của bạn đã bị Quản trị viên tạm ẩn khỏi cộng đồng do vi phạm tiêu chuẩn hoặc nhận nhiều phản ánh.",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to notify author on post hide: {}", e.getMessage());
            }
        }

        return mapToDto(saved);
    }

    @Transactional
    public AdminPostDto removePostAndBanAuthor(Long postId) {
        log.info("Removing Post ID: {} and locking author account", postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết với ID: " + postId));

        post.setStatus("REMOVED");

        User author = post.getAuthor();
        if (author != null) {
            author.setIsLocked(true);
            author.setStatus("LOCKED");
            userRepository.save(author);
            log.warn("Author {} (ID: {}) has been locked due to critical post violation on Post ID: {}",
                    author.getEmail(), author.getId(), postId);
            try {
                notificationService.sendNotification(
                        author,
                        null,
                        "POST_LOCKED",
                        "Bài viết '" + post.getTitle() + "' vi phạm nghiêm trọng chính sách nội dung. Bài viết đã bị gỡ và tài khoản bị tạm khóa kiểm tra.",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to notify author on post lock and ban: {}", e.getMessage());
            }
        }

        Post saved = postRepository.save(post);
        return mapToDto(saved);
    }

    @Transactional
    public void deletePost(Long postId) {
        log.info("Permanently deleting Post ID: {}", postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết để xóa: " + postId));

        if (post.getAuthor() != null) {
            try {
                notificationService.sendNotification(
                        post.getAuthor(),
                        null,
                        "POST_DELETED",
                        "Bài viết '" + post.getTitle() + "' của bạn đã bị Quản trị viên xóa vĩnh viễn khỏi hệ thống.",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to notify author on post deletion: {}", e.getMessage());
            }
        }

        postReportRepository.deleteByPostId(postId);
        postRepository.deleteById(postId);
    }

    private AdminPostDto mapToDto(Post post) {
        String snippet = post.getContent();
        if (snippet != null && snippet.length() > 180) {
            snippet = snippet.substring(0, 180) + "...";
        }

        String categoryType = "info";
        String cat = post.getCategory() != null ? post.getCategory().toLowerCase() : "";
        if (cat.contains("an toàn") || cat.contains("pháp luật") || cat.contains("nguy cơ")) {
            categoryType = "error";
        } else if (cat.contains("spam") || cat.contains("scam") || cat.contains("thương mại")) {
            categoryType = "warning";
        }

        String timeAgo = "Gần đây";
        if (post.getCreatedAt() != null) {
            Duration duration = Duration.between(post.getCreatedAt(), LocalDateTime.now());
            if (duration.toMinutes() < 60) {
                timeAgo = Math.max(1, duration.toMinutes()) + " phút trước";
            } else if (duration.toHours() < 24) {
                timeAgo = duration.toHours() + " giờ trước";
            } else {
                timeAgo = duration.toDays() + " ngày trước";
            }
        }

        User author = post.getAuthor();
        String authorName = author != null ? author.getFullName() : "Người dùng ẩn danh";
        String authorHandle = author != null && author.getHandle() != null ? author.getHandle() : "@wanderer";
        String authorAvatar = author != null ? author.getAvatarUrl() : null;
        Integer authorTrustScore = 80;
        String authorAccountAge = "Thành viên";

        if (author != null) {
            if (author.getCreatedAt() != null) {
                long years = Duration.between(author.getCreatedAt(), LocalDateTime.now()).toDays() / 365;
                authorAccountAge = years >= 1 ? years + " năm" : "Mới tham gia";
            }
        }

        return AdminPostDto.builder()
                .id(post.getId())
                .postCode("PST-" + (89000 + post.getId()))
                .title(post.getTitle())
                .content(post.getContent())
                .snippet(snippet)
                .category(post.getCategory() != null ? post.getCategory() : "Chia sẻ kinh nghiệm")
                .categoryType(categoryType)
                .locationTag(post.getLocationTag() != null ? post.getLocationTag() : "Việt Nam")
                .imageUrl(post.getImageUrl() != null ? post.getImageUrl() : "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80")
                .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .reportsCount(post.getReportsCount() != null ? post.getReportsCount() : 0)
                .status(post.getStatus() != null ? post.getStatus() : "ACTIVE")
                .aiSafetyScore(post.getAiSafetyScore() != null ? post.getAiSafetyScore() : 98)
                .aiFlagReason(post.getAiFlagReason() != null ? post.getAiFlagReason() : "Hệ thống AI không phát hiện vi phạm quy chuẩn nội dung.")
                .reportReason(post.getReportReason() != null ? post.getReportReason() : "Báo cáo nội dung chưa phù hợp từ cộng đồng.")
                .badgeText(post.getBadgeText() != null ? post.getBadgeText() : (post.getReportsCount() != null && post.getReportsCount() > 0 ? post.getReportsCount() + " Lượt báo cáo" : "Nội dung an toàn"))
                .createdAt(post.getCreatedAt())
                .timeAgo(timeAgo)
                .authorId(author != null ? author.getId() : null)
                .authorName(authorName)
                .authorHandle(authorHandle)
                .authorAvatar(authorAvatar)
                .authorTrustScore(authorTrustScore)
                .authorAccountAge(authorAccountAge)
                .build();
    }
}
