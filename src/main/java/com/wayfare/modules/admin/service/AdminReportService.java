package com.wayfare.modules.admin.service;

import com.wayfare.modules.notification.service.NotificationService;

import com.wayfare.modules.admin.dto.AdminPostDto;
import com.wayfare.modules.admin.dto.AdminReportMetricsDto;
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
    private final com.wayfare.repository.PostLikeRepository postLikeRepository;
    private final com.wayfare.repository.PostBookmarkRepository postBookmarkRepository;
    private final com.wayfare.repository.PostCommentRepository postCommentRepository;
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
                .filter(p -> ("PENDING_REVIEW".equalsIgnoreCase(p.getStatus()) || "PENDING_REPORT".equalsIgnoreCase(p.getStatus()) || (p.getReportsCount() != null && p.getReportsCount() > 0)) &&
                             !"HIDDEN".equalsIgnoreCase(p.getStatus()) && !"REMOVED".equalsIgnoreCase(p.getStatus()) && !"DISMISSED".equalsIgnoreCase(p.getStatus()))
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
                .filter(p -> ("PENDING_REVIEW".equalsIgnoreCase(p.getStatus()) || "PENDING_REPORT".equalsIgnoreCase(p.getStatus()) || (p.getReportsCount() != null && p.getReportsCount() > 0)) &&
                             !"HIDDEN".equalsIgnoreCase(p.getStatus()) && !"REMOVED".equalsIgnoreCase(p.getStatus()) && !"DISMISSED".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());

        if (keyword != null && !keyword.trim().isEmpty()) {
            String q = keyword.trim().toLowerCase();
            posts = posts.stream()
                    .filter(p -> (p.getTitle() != null && p.getTitle().toLowerCase().contains(q)) ||
                                 (p.getAuthor() != null && p.getAuthor().getFullName() != null && p.getAuthor().getFullName().toLowerCase().contains(q)) ||
                                 (p.getLocationTag() != null && p.getLocationTag().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        List<AdminPostDto> dtos = posts.stream().map(this::mapToDto).collect(Collectors.toList());

        // Sort by newest activity: latest report timestamp (for user reports) or post createdAt (for AI review)
        dtos.sort((a, b) -> {
            LocalDateTime timeA = a.getLatestReportedAt() != null ? a.getLatestReportedAt() : a.getCreatedAt();
            LocalDateTime timeB = b.getLatestReportedAt() != null ? b.getLatestReportedAt() : b.getCreatedAt();
            if (timeA != null && timeB != null) {
                int cmp = timeB.compareTo(timeA);
                if (cmp != 0) return cmp;
            }
            if (b.getId() != null && a.getId() != null) {
                return b.getId().compareTo(a.getId());
            }
            return 0;
        });

        return dtos;
    }

    public List<AdminPostDto> getAllArticles(String keyword, String status) {
        log.info("Fetching all community posts for moderation, keyword: '{}', status: '{}'", keyword, status);
        String filterKeyword = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim().toLowerCase();

        List<Post> posts;
        if ("hidden".equalsIgnoreCase(status)) {
            // Include both HIDDEN and REMOVED posts for hidden tab
            posts = postRepository.findAll().stream()
                    .filter(p -> "HIDDEN".equalsIgnoreCase(p.getStatus()) || "REMOVED".equalsIgnoreCase(p.getStatus()))
                    .collect(Collectors.toList());
        } else if (status != null && !"all".equalsIgnoreCase(status) && !"pending".equalsIgnoreCase(status)) {
            posts = postRepository.searchPosts(filterKeyword, status.toUpperCase());
        } else {
            posts = postRepository.searchPosts(filterKeyword, null);
        }

        if (filterKeyword != null) {
            posts = posts.stream()
                    .filter(p -> (p.getTitle() != null && p.getTitle().toLowerCase().contains(filterKeyword)) ||
                                 (p.getAuthor() != null && p.getAuthor().getFullName() != null && p.getAuthor().getFullName().toLowerCase().contains(filterKeyword)) ||
                                 (p.getLocationTag() != null && p.getLocationTag().toLowerCase().contains(filterKeyword)) ||
                                 String.valueOf(p.getId()).contains(filterKeyword))
                    .collect(Collectors.toList());
        }

        List<AdminPostDto> dtos = posts.stream().map(this::mapToDto).collect(Collectors.toList());

        // Sort by newest: for hidden tab sort by updatedAt (when hidden) descending; for all tab sort by createdAt descending
        dtos.sort((a, b) -> {
            LocalDateTime timeA = "hidden".equalsIgnoreCase(status) ? (a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt()) : a.getCreatedAt();
            LocalDateTime timeB = "hidden".equalsIgnoreCase(status) ? (b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt()) : b.getCreatedAt();
            if (timeA != null && timeB != null) {
                int cmp = timeB.compareTo(timeA);
                if (cmp != 0) return cmp;
            }
            if (b.getId() != null && a.getId() != null) {
                return b.getId().compareTo(a.getId());
            }
            return 0;
        });

        return dtos;
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
        post.setUpdatedAt(LocalDateTime.now());
        Post saved = postRepository.save(post);

        // Update post reports status so they are resolved
        List<com.wayfare.entity.PostReport> reports = postReportRepository.findByPostId(postId);
        for (com.wayfare.entity.PostReport r : reports) {
            r.setStatus("RESOLVED_HIDDEN");
            r.setModeratorNotes("Bài viết đã được Quản trị viên xử lý và tạm ẩn.");
            postReportRepository.save(r);
        }

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
    public AdminPostDto unhidePost(Long postId) {
        log.info("Unhiding/moving Post ID: {} back to PENDING_REVIEW moderation queue", postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết với ID: " + postId));

        post.setStatus("PENDING_REVIEW");
        post.setReportsCount(0);
        Post saved = postRepository.save(post);

        // Notify author
        if (post.getAuthor() != null) {
            try {
                notificationService.sendNotification(
                        post.getAuthor(),
                        null,
                        "POST_PENDING_REVIEW",
                        "Bài viết '" + post.getTitle() + "' của bạn đã được Quản trị viên đưa trở lại hàng đợi chờ duyệt để xem xét xuất bản.",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to notify author on post unhide: {}", e.getMessage());
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

        postLikeRepository.deleteByPostId(postId);
        postBookmarkRepository.deleteByPostId(postId);
        postCommentRepository.deleteByPostId(postId);
        postReportRepository.deleteByPostId(postId);
        postRepository.deleteById(postId);
    }

    private AdminPostDto mapToDto(Post post) {
        String snippet = post.getContent();
        if (snippet != null && snippet.length() > 180) {
            snippet = snippet.substring(0, 180) + "...";
        }

        // Look up user reports for this post to provide actual reported category & reporter details
        List<com.wayfare.entity.PostReport> reports = postReportRepository.findByPostId(post.getId());
        com.wayfare.entity.PostReport latestReport = null;
        if (reports != null && !reports.isEmpty()) {
            latestReport = reports.stream()
                    .max(java.util.Comparator.comparing(r -> r.getId() != null ? r.getId() : 0L))
                    .orElse(null);
        }

        boolean isPendingReview = "PENDING_REVIEW".equals(post.getStatus());
        String effectiveCategory = (latestReport != null && latestReport.getCategory() != null && !latestReport.getCategory().isBlank())
                ? latestReport.getCategory()
                : (post.getCategory() != null ? post.getCategory() : "Chia sẻ kinh nghiệm");

        String categoryType = "info";
        String cat = effectiveCategory.toLowerCase();
        if (cat.contains("an toàn") || cat.contains("pháp luật") || cat.contains("nguy cơ") ||
            cat.contains("lừa đảo") || cat.contains("đòi nợ") || cat.contains("bạo lực") ||
            cat.contains("phản cảm") || cat.contains("bôi nhọ") || cat.contains("quấy rối")) {
            categoryType = "error";
        } else if (cat.contains("spam") || cat.contains("scam") || cat.contains("thương mại") || cat.contains("bản quyền")) {
            categoryType = "warning";
        }

        Long reporterId = null;
        String reporterName = null;
        String reporterEmail = null;
        LocalDateTime latestReportedAt = null;
        String reportReasonText = post.getReportReason();

        if (latestReport != null) {
            if (latestReport.getReason() != null && !latestReport.getReason().isBlank()) {
                reportReasonText = latestReport.getReason();
            }
            if (latestReport.getReporter() != null) {
                reporterId = latestReport.getReporter().getId();
                reporterName = latestReport.getReporter().getFullName();
                reporterEmail = latestReport.getReporter().getEmail();
            }
            latestReportedAt = latestReport.getCreatedAt() != null ? latestReport.getCreatedAt() : post.getUpdatedAt();
        }

        if (reportReasonText == null || reportReasonText.isBlank()) {
            if (isPendingReview) {
                reportReasonText = post.getAiFlagReason() != null ? post.getAiFlagReason() : "Bài viết do người dùng đăng tải bị hệ thống AI giữ lại chờ duyệt thủ công.";
            } else {
                reportReasonText = "Báo cáo nội dung cần rà soát từ cộng đồng.";
            }
        }

        // Calculate timeAgo accurately:
        // For hidden/removed posts: use updatedAt (when post was hidden/removed), fallback to createdAt
        // For active/reported posts: use latest report time if available, otherwise createdAt
        LocalDateTime referenceTime;
        if ("HIDDEN".equalsIgnoreCase(post.getStatus()) || "REMOVED".equalsIgnoreCase(post.getStatus())) {
            referenceTime = post.getUpdatedAt() != null ? post.getUpdatedAt() : post.getCreatedAt();
        } else if (latestReportedAt != null) {
            referenceTime = latestReportedAt;
        } else {
            referenceTime = post.getCreatedAt();
        }
        String timeAgo = calculateTimeAgo(referenceTime);

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

        String defaultBadgeText;
        if (isPendingReview) {
            defaultBadgeText = "AI gắn cờ - Chờ duyệt";
            if ("info".equals(categoryType)) categoryType = "warning";
        } else if (post.getReportsCount() != null && post.getReportsCount() > 0) {
            defaultBadgeText = post.getReportsCount() + " Lượt báo cáo";
        } else {
            defaultBadgeText = "Nội dung an toàn";
        }

        return AdminPostDto.builder()
                .id(post.getId())
                .postCode("PST-" + (89000 + post.getId()))
                .title(post.getTitle())
                .content(post.getContent())
                .snippet(snippet)
                .category(effectiveCategory)
                .categoryType(categoryType)
                .locationTag(post.getLocationTag() != null ? post.getLocationTag() : "Việt Nam")
                .imageUrl(sanitizeMediaUrl(post.getFirstImage() != null ? post.getFirstImage() : "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"))
                .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .reportsCount(post.getReportsCount() != null ? post.getReportsCount() : (latestReport != null ? 1 : 0))
                .status(post.getStatus() != null ? post.getStatus() : "ACTIVE")
                .aiSafetyScore(post.getAiSafetyScore() != null ? post.getAiSafetyScore() : 98)
                .aiFlagReason(post.getAiFlagReason() != null ? post.getAiFlagReason() : "Hệ thống AI không phát hiện vi phạm quy chuẩn nội dung.")
                .reportReason(reportReasonText)
                .badgeText(post.getBadgeText() != null && !post.getBadgeText().isBlank() ? post.getBadgeText() : defaultBadgeText)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .timeAgo(timeAgo)
                .authorId(author != null ? author.getId() : null)
                .authorName(authorName)
                .authorHandle(authorHandle)
                .authorAvatar(sanitizeMediaUrl(authorAvatar))
                .authorTrustScore(authorTrustScore)
                .authorAccountAge(authorAccountAge)
                .reporterId(reporterId)
                .reporterName(reporterName)
                .reporterEmail(reporterEmail)
                .latestReportedAt(latestReportedAt)
                .build();
    }

    private String sanitizeMediaUrl(String url) {
        if (url == null || url.isBlank()) return url;

        String baseUrl = null;
        try {
            org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest req = ((org.springframework.web.context.request.ServletRequestAttributes) attrs).getRequest();
                String proto = req.getHeader("X-Forwarded-Proto");
                String host = req.getHeader("X-Forwarded-Host");
                if (host == null || host.isBlank()) {
                    host = req.getHeader("Host");
                }
                if (host != null && !host.isBlank() && !host.contains("localhost")) {
                    String scheme = (proto != null && !proto.isBlank()) ? proto : req.getScheme();
                    baseUrl = scheme + "://" + host;
                }
            }
        } catch (Exception ignored) {}

        if (baseUrl == null) {
            String railwayDomain = System.getenv("RAILWAY_PUBLIC_DOMAIN");
            if (railwayDomain == null || railwayDomain.isBlank()) {
                railwayDomain = System.getenv("RAILWAY_STATIC_URL");
            }
            if (railwayDomain != null && !railwayDomain.isBlank()) {
                baseUrl = railwayDomain.startsWith("http") ? railwayDomain : "https://" + railwayDomain;
            }
        }

        if (baseUrl != null && !baseUrl.contains("localhost")) {
            if (url.startsWith("http://localhost:8081")) {
                return url.replace("http://localhost:8081", baseUrl);
            }
            if (url.startsWith("http://localhost:")) {
                return url.replaceFirst("^http://localhost:\\d+", baseUrl);
            }
        }
        return url;
    }

    public String calculateTimeAgo(LocalDateTime targetTime) {
        if (targetTime == null) return "Vừa xong";
        Duration duration = Duration.between(targetTime, LocalDateTime.now());
        long seconds = duration.getSeconds();
        // Handle slight clock drift between DB & JVM
        if (seconds < 60) {
            return "Vừa xong";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " giờ trước";
        }
        long days = hours / 24;
        if (days < 30) {
            return days + " ngày trước";
        }
        long months = days / 30;
        if (months < 12) {
            return months + " tháng trước";
        }
        return (months / 12) + " năm trước";
    }
}


