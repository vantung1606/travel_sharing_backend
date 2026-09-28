package com.wayfare.service;

import com.wayfare.dto.*;
import com.wayfare.entity.*;
import com.wayfare.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDetailRepository itineraryDetailRepository;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final AiContentModerationService aiContentModerationService;
    private final PostReportRepository postReportRepository;

    @Transactional(readOnly = true)
    public List<PostDto> getCommunityPosts(String category, String keyword, String currentUserEmail) {
        log.info("Fetching community posts with category='{}', keyword='{}', user='{}'", category, keyword, currentUserEmail);

        User currentUser = null;
        if (currentUserEmail != null && !currentUserEmail.isBlank()) {
            currentUser = userRepository.findByEmail(currentUserEmail).orElse(null);
        }

        List<Post> posts;
        if (keyword != null && !keyword.isBlank()) {
            posts = postRepository.searchPosts(keyword.trim(), "ACTIVE");
        } else {
            posts = postRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
        }

        // If a user is logged in, also show their own PENDING_REVIEW posts with an amber banner
        if (currentUser != null) {
            final Long currentUserId = currentUser.getId();
            List<Post> myPending = postRepository.findAll().stream()
                    .filter(p -> p.getAuthor() != null && p.getAuthor().getId().equals(currentUserId) && "PENDING_REVIEW".equals(p.getStatus()))
                    .collect(Collectors.toList());
            if (!myPending.isEmpty()) {
                posts = new ArrayList<>(posts);
                posts.addAll(0, myPending);
            }
        }

        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("ALL") && !category.equalsIgnoreCase("Tất cả")) {
            posts = posts.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().toLowerCase().contains(category.toLowerCase()))
                    .collect(Collectors.toList());
        }

        final User loggedInUser = currentUser;
        return posts.stream().map(p -> mapToDto(p, loggedInUser)).collect(Collectors.toList());
    }

    private User resolveUser(String email) {
        if (email != null && !email.trim().isEmpty()) {
            Optional<User> found = userRepository.findByEmail(email.trim());
            if (found.isPresent()) return found.get();
        }
        return userRepository.findByEmail("tung@gmail.com")
                .or(() -> userRepository.findByEmail("admin@gmail.com"))
                .orElseGet(() -> userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("Chưa có tài khoản người dùng trong hệ thống!")));
    }

    @Transactional(readOnly = true)
    public PostDto getPostById(Long id, String currentUserEmail) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại với ID: " + id));
        User currentUser = resolveUser(currentUserEmail);
        return mapToDto(post, currentUser);
    }

    @Transactional
    public PostDto createPost(CreatePostRequest request, String currentUserEmail, HttpServletRequest httpRequest) {
        log.info("Creating new community post by user '{}': {}", currentUserEmail, request.getTitle());

        User author = resolveUser(currentUserEmail);

        Itinerary attachedItinerary = null;
        if (request.getItineraryId() != null) {
            attachedItinerary = itineraryRepository.findById(request.getItineraryId()).orElse(null);
        }

        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            title = request.getContent().length() > 50 ? request.getContent().substring(0, 47) + "..." : request.getContent();
        }

        String primaryImage = request.getImageUrl();
        if ((primaryImage == null || primaryImage.isBlank()) && request.getImages() != null && !request.getImages().isEmpty()) {
            primaryImage = request.getImages().get(0);
        }
        if (primaryImage == null || primaryImage.isBlank()) {
            if (attachedItinerary != null && attachedItinerary.getCoverImageUrl() != null) {
                primaryImage = attachedItinerary.getCoverImageUrl();
            } else {
                primaryImage = "https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=800&q=80";
            }
        }

        // 1. Run WanderAI Content Moderation & Safety Inspection
        AiContentModerationService.ModerationResult aiResult =
                aiContentModerationService.moderate(title, request.getContent(), request.getLocationTag(), request.getCategory());

        String initialStatus = aiResult.isApproved() ? "ACTIVE" : "PENDING_REVIEW";
        String postCategory = aiResult.isApproved()
                ? (request.getCategory() != null && !request.getCategory().isBlank() ? request.getCategory() : "Chia sẻ hành trình")
                : aiResult.getCategory();

        Post newPost = Post.builder()
                .author(author)
                .title(title)
                .content(request.getContent())
                .locationTag(request.getLocationTag() != null && !request.getLocationTag().isBlank() ? request.getLocationTag() : (attachedItinerary != null ? attachedItinerary.getDestination() : "Việt Nam"))
                .imageUrl(primaryImage)
                .category(postCategory)
                .badgeText(aiResult.getBadgeText())
                .itinerary(attachedItinerary)
                .likeCount(0)
                .commentCount(0)
                .reportsCount(aiResult.isApproved() ? 0 : 1)
                .reportReason(aiResult.getFlagReason())
                .status(initialStatus)
                .aiSafetyScore(aiResult.getSafetyScore())
                .aiFlagReason(aiResult.getFlagReason())
                .build();

        Post saved = postRepository.save(newPost);
        log.info("Community post saved successfully with ID: {}, Status: {}, AI Score: {}", saved.getId(), saved.getStatus(), saved.getAiSafetyScore());

        // Audit log & Notifications
        String ip = activityLogService.extractClientIp(httpRequest);
        String ua = httpRequest != null && httpRequest.getHeader("User-Agent") != null ? httpRequest.getHeader("User-Agent") : "Web Client";

        if (aiResult.isApproved()) {
            activityLogService.recordLog(author, "CREATE_POST", "Đăng bài viết mới lên Cộng đồng (AI duyệt tự động): '" + saved.getTitle() + "'", ip, ua);

            // Send success notification to author
            try {
                notificationService.sendNotification(
                        author,
                        null,
                        "AI_READY",
                        "Chúc mừng! Bài viết '" + saved.getTitle() + "' của bạn đã vượt qua thẩm định AI an toàn (Điểm an toàn: " + aiResult.getSafetyScore() + "/100) và đã được đăng công khai!",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to send post success notification: {}", e.getMessage());
            }
        } else {
            activityLogService.recordLog(author, "AI_FLAG_POST", "AI gắn cờ bài viết vi phạm, chuyển hàng đợi Admin duyệt: '" + saved.getTitle() + "'", ip, ua);

            // Save PostReport for Admin Escalation
            try {
                com.wayfare.entity.PostReport report = com.wayfare.entity.PostReport.builder()
                        .post(saved)
                        .reporter(author)
                        .category(aiResult.getCategory())
                        .reason(aiResult.getFlagReason())
                        .aiAnalysisSnippet("WanderAI Shield: Điểm an toàn " + aiResult.getSafetyScore() + "/100. Các vi phạm: " + String.join("; ", aiResult.getFlaggedViolations()))
                        .status("PENDING")
                        .build();
                postReportRepository.save(report);
            } catch (Exception e) {
                log.warn("Failed to save automated post report: {}", e.getMessage());
            }

            // Send Pending Moderation notification to author
            try {
                String briefReason = aiResult.getFlagReason() != null && aiResult.getFlagReason().length() > 100
                        ? aiResult.getFlagReason().substring(0, 97) + "..."
                        : aiResult.getFlagReason();

                notificationService.sendNotification(
                        author,
                        null,
                        "SYSTEM",
                        "Bài viết '" + saved.getTitle() + "' đang ở trạng thái CHỜ DUYỆT THỦ CÔNG bởi Quản trị viên (Lý do: " + briefReason + "). Bài viết sẽ được xuất bản sau khi Admin phê duyệt.",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to send pending moderation notification: {}", e.getMessage());
            }

            // Send notification to Admin
            try {
                User admin = userRepository.findByEmail("admin@gmail.com").orElse(null);
                if (admin != null) {
                    notificationService.sendNotification(
                            admin,
                            author,
                            "SYSTEM",
                            "Cần duyệt bài viết mới từ " + author.getFullName() + ": '" + saved.getTitle() + "' (Điểm AI: " + aiResult.getSafetyScore() + "/100).",
                            "/admin/reports"
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to send admin alert notification: {}", e.getMessage());
            }
        }

        return mapToDto(saved, author);
    }

    @Transactional
    public Map<String, Object> toggleLikePost(Long postId, String currentUserEmail) {
        log.info("Toggle like for post {} by {}", postId, currentUserEmail);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + postId));

        User user = resolveUser(currentUserEmail);

        Optional<PostLike> existingLike = postLikeRepository.findByUserIdAndPostId(user.getId(), postId);
        boolean isLiked;

        if (existingLike.isPresent()) {
            postLikeRepository.delete(existingLike.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            isLiked = false;
        } else {
            PostLike newLike = PostLike.builder()
                    .user(user)
                    .post(post)
                    .build();
            postLikeRepository.save(newLike);
            post.setLikeCount(post.getLikeCount() + 1);
            isLiked = true;

            // Send notification to author if not liking own post
            if (!post.getAuthor().getId().equals(user.getId())) {
                try {
                    notificationService.sendNotification(
                            post.getAuthor(),
                            user,
                            "LIKE",
                            user.getFullName() + " đã thích bài viết '" + post.getTitle() + "' của bạn.",
                            "/community"
                    );
                } catch (Exception e) {
                    log.warn("Failed to send LIKE notification: {}", e.getMessage());
                }
            }
        }

        postRepository.save(post);
        return Map.of("isLiked", isLiked, "likeCount", post.getLikeCount());
    }

    @Transactional(readOnly = true)
    public List<CommentDto> getPostComments(Long postId) {
        List<PostComment> comments = postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        return comments.stream().map(this::mapCommentToDto).collect(Collectors.toList());
    }

    @Transactional
    public CommentDto addComment(Long postId, CreateCommentRequest request, String currentUserEmail) {
        log.info("Adding comment to post {} by user {}: {}", postId, currentUserEmail, request.getContent());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + postId));

        User user = resolveUser(currentUserEmail);

        PostComment comment = PostComment.builder()
                .post(post)
                .author(user)
                .content(request.getContent())
                .build();

        PostComment saved = postCommentRepository.save(comment);

        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        // Send notification to author if not commenting on own post
        if (!post.getAuthor().getId().equals(user.getId())) {
            try {
                notificationService.sendNotification(
                        post.getAuthor(),
                        user,
                        "COMMENT",
                        user.getFullName() + " đã bình luận về bài viết của bạn: '" + (request.getContent().length() > 40 ? request.getContent().substring(0, 37) + "..." : request.getContent()) + "'",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to send COMMENT notification: {}", e.getMessage());
            }
        }

        return mapCommentToDto(saved);
    }

    @Transactional
    public ItineraryDto cloneItineraryFromPost(Long postId, String currentUserEmail) {
        log.info("Cloning itinerary from post {} for user {}", postId, currentUserEmail);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + postId));

        if (post.getItinerary() == null) {
            throw new RuntimeException("Bài viết này không có lộ trình đính kèm!");
        }

        User user = resolveUser(currentUserEmail);

        Itinerary original = post.getItinerary();

        Itinerary clone = Itinerary.builder()
                .creator(user)
                .title("Bản sao: " + original.getTitle())
                .destination(original.getDestination())
                .startDate(java.time.LocalDate.now().plusDays(7))
                .endDate(java.time.LocalDate.now().plusDays(10))
                .budgetTotal(original.getBudgetTotal())
                .coverImageUrl(original.getCoverImageUrl())
                .isAiGenerated(original.getIsAiGenerated())
                .status("ACTIVE")
                .build();

        Itinerary savedClone = itineraryRepository.save(clone);

        // Clone details if any exist
        List<ItineraryDetail> details = itineraryDetailRepository.findByItineraryIdOrderByDayNumberAscVisitOrderAsc(original.getId());
        for (ItineraryDetail d : details) {
            ItineraryDetail detailClone = ItineraryDetail.builder()
                    .itinerary(savedClone)
                    .dayNumber(d.getDayNumber())
                    .visitOrder(d.getVisitOrder())
                    .startTime(d.getStartTime())
                    .place(d.getPlace())
                    .locationName(d.getLocationName())
                    .locationAddress(d.getLocationAddress())
                    .category(d.getCategory())
                    .aiTip(d.getAiTip())
                    .transitInfo(d.getTransitInfo())
                    .note(d.getNote())
                    .estimatedCost(d.getEstimatedCost())
                    .build();
            itineraryDetailRepository.save(detailClone);
        }

        log.info("Successfully cloned itinerary {} to new itinerary ID: {}", original.getId(), savedClone.getId());

        // Send self notification
        try {
            notificationService.sendNotification(
                    user,
                    post.getAuthor(),
                    "AI_READY",
                    "Bạn đã sao chép thành công chuyến đi '" + original.getTitle() + "' từ " + post.getAuthor().getFullName() + " vào kho Lịch trình của bạn!",
                    "/itineraries"
            );
        } catch (Exception e) {
            log.warn("Failed to send notification for itinerary clone: {}", e.getMessage());
        }

        return ItineraryDto.builder()
                .id(savedClone.getId())
                .title(savedClone.getTitle())
                .destination(savedClone.getDestination())
                .coverImageUrl(savedClone.getCoverImageUrl())
                .isAiGenerated(savedClone.getIsAiGenerated())
                .status(savedClone.getStatus())
                .build();
    }

    private PostDto mapToDto(Post p, User currentUser) {
        boolean liked = false;
        if (currentUser != null) {
            liked = postLikeRepository.existsByUserIdAndPostId(currentUser.getId(), p.getId());
        }

        Itinerary itin = p.getItinerary();
        List<String> images = new ArrayList<>();
        if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
            images.add(p.getImageUrl());
        }

        return PostDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .content(p.getContent())
                .locationTag(p.getLocationTag())
                .imageUrl(p.getImageUrl())
                .images(images)
                .likeCount(p.getLikeCount() != null ? p.getLikeCount() : 0)
                .commentCount(p.getCommentCount() != null ? p.getCommentCount() : 0)
                .isLiked(liked)
                .category(p.getCategory())
                .status(p.getStatus())
                .aiSafetyScore(p.getAiSafetyScore())
                .aiFlagReason(p.getAiFlagReason())
                .badgeText(p.getBadgeText())
                .createdAt(p.getCreatedAt())
                .timeAgo(formatTimeAgo(p.getCreatedAt()))
                .authorId(p.getAuthor() != null ? p.getAuthor().getId() : null)
                .authorName(p.getAuthor() != null ? p.getAuthor().getFullName() : "Du khách Wayfare")
                .authorHandle(p.getAuthor() != null ? p.getAuthor().getHandle() : "@wayfarer")
                .authorAvatar(p.getAuthor() != null ? p.getAuthor().getAvatarUrl() : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .authorRole(p.getAuthor() != null && p.getAuthor().getRoles() != null && p.getAuthor().getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN")) ? "Quản trị viên" : "Phượt thủ tự do")
                // Attached Itinerary
                .itineraryId(itin != null ? itin.getId() : null)
                .itineraryTitle(itin != null ? itin.getTitle() : null)
                .itineraryDestination(itin != null ? itin.getDestination() : null)
                .itineraryDuration(itin != null ? "3N2Đ" : null)
                .itineraryBudget(itin != null && itin.getBudgetTotal() != null ? itin.getBudgetTotal().longValue() : 3850000L)
                .itineraryPlacesCount(itin != null ? 8 : null)
                .itineraryIsAi(itin != null ? itin.getIsAiGenerated() : false)
                .build();
    }

    private CommentDto mapCommentToDto(PostComment c) {
        return CommentDto.builder()
                .id(c.getId())
                .postId(c.getPost().getId())
                .authorId(c.getAuthor().getId())
                .authorName(c.getAuthor().getFullName())
                .authorHandle(c.getAuthor().getHandle())
                .authorAvatar(c.getAuthor().getAvatarUrl())
                .content(c.getContent())
                .createdAt(c.getCreatedAt())
                .timeAgo(formatTimeAgo(c.getCreatedAt()))
                .build();
    }

    private String formatTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "Vừa xong";
        Duration diff = Duration.between(dateTime, LocalDateTime.now());
        long seconds = diff.getSeconds();
        if (seconds < 60) return "Vừa xong";
        if (seconds < 3600) return (seconds / 60) + " phút trước";
        if (seconds < 86400) return (seconds / 3600) + " giờ trước";
        if (seconds < 86400 * 2) return "Hôm qua";
        return (seconds / 86400) + " ngày trước";
    }
}
