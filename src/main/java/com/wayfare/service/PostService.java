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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.wayfare.exception.ResourceNotFoundException;

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
    private final PostBookmarkRepository postBookmarkRepository;

    @Transactional(readOnly = true)
    public List<PostDto> getCommunityPosts(String category, String keyword, String currentUserEmail) {
        log.info("Fetching community posts with category='{}', keyword='{}', user='{}'", category, keyword, currentUserEmail);

        final User currentUser = (currentUserEmail != null && !currentUserEmail.isBlank())
                ? resolveUser(currentUserEmail)
                : null;
        final Long currentUserId = currentUser != null ? currentUser.getId() : null;

        List<Post> posts;
        if (keyword != null && !keyword.isBlank()) {
            posts = postRepository.searchPosts(keyword.trim(), "ACTIVE");
        } else {
            posts = postRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
        }

        // Filter out PRIVATE posts unless viewed by the author
        posts = posts.stream()
                .filter(p -> {
                    if ("PRIVATE".equalsIgnoreCase(p.getVisibility())) {
                        return currentUserId != null && p.getAuthor() != null && currentUserId.equals(p.getAuthor().getId());
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // If a user is logged in, also show their own PENDING_REVIEW posts with an amber banner
        if (currentUser != null) {
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
        return userRepository.findByEmail("tung_2251220254@dau.edu.vn")
                .or(() -> userRepository.findByEmail("tung@gmail.com"))
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
                aiContentModerationService.moderate(title, request.getContent(), request.getLocationTag(), request.getCategory(), attachedItinerary != null);

        String initialStatus = aiResult.isApproved() ? "ACTIVE" : "PENDING_REVIEW";
        String postCategory = aiResult.isApproved()
                ? (request.getCategory() != null && !request.getCategory().isBlank() ? request.getCategory() : "Chia sẻ hành trình")
                : aiResult.getCategory();

        String imagesStr = null;
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            imagesStr = String.join(";;;", request.getImages());
        }

        Post newPost = Post.builder()
                .author(author)
                .title(title)
                .content(request.getContent())
                .locationTag(request.getLocationTag() != null && !request.getLocationTag().isBlank() ? request.getLocationTag() : (attachedItinerary != null ? attachedItinerary.getDestination() : "Việt Nam"))
                .imageUrl(primaryImage)
                .images(imagesStr)
                .videoUrl(request.getVideoUrl())
                .visibility(request.getVisibility() != null && !request.getVisibility().isBlank() ? request.getVisibility() : "PUBLIC")
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

    @Transactional
    public Map<String, Object> toggleBookmarkPost(Long postId, String currentUserEmail) {
        log.info("Toggling bookmark on post {} by user {}", postId, currentUserEmail);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại với ID: " + postId));

        User user = resolveUser(currentUserEmail);

        Optional<PostBookmark> existingBookmark = postBookmarkRepository.findByUserIdAndPostId(user.getId(), postId);
        boolean isBookmarked;

        if (existingBookmark.isPresent()) {
            postBookmarkRepository.delete(existingBookmark.get());
            isBookmarked = false;
            log.info("User {} removed bookmark on post {}", user.getEmail(), postId);
        } else {
            PostBookmark newBookmark = PostBookmark.builder()
                    .user(user)
                    .post(post)
                    .build();
            postBookmarkRepository.save(newBookmark);
            isBookmarked = true;
            log.info("User {} saved bookmark on post {}", user.getEmail(), postId);
        }

        return Map.of("isBookmarked", isBookmarked, "postId", postId);
    }

    @Transactional(readOnly = true)
    public List<Long> getBookmarkedPostIds(String currentUserEmail) {
        User user = resolveUser(currentUserEmail);
        return postBookmarkRepository.findBookmarkedPostIdsByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<PostDto> getBookmarkedPosts(String currentUserEmail) {
        User user = resolveUser(currentUserEmail);
        List<PostBookmark> bookmarks = postBookmarkRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return bookmarks.stream()
                .map(PostBookmark::getPost)
                .filter(p -> p != null && !"DELETED".equalsIgnoreCase(p.getStatus()))
                .map(p -> mapToDto(p, user))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CommentDto> getPostComments(Long postId) {
        List<PostComment> allComments = postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        Map<Long, CommentDto> dtoMap = new LinkedHashMap<>();
        List<CommentDto> rootComments = new ArrayList<>();

        for (PostComment c : allComments) {
            CommentDto dto = mapCommentToDto(c);
            dtoMap.put(c.getId(), dto);
        }

        for (PostComment c : allComments) {
            CommentDto dto = dtoMap.get(c.getId());
            if (c.getParent() != null && dtoMap.containsKey(c.getParent().getId())) {
                CommentDto parentDto = dtoMap.get(c.getParent().getId());
                if (parentDto.getReplies() == null) {
                    parentDto.setReplies(new ArrayList<>());
                }
                parentDto.getReplies().add(dto);
            } else {
                rootComments.add(dto);
            }
        }

        return rootComments;
    }

    @Transactional
    public CommentDto addComment(Long postId, CreateCommentRequest request, String currentUserEmail) {
        log.info("Adding comment to post {} by user {}: parentId={}, replyToUserId={}, content={}",
                postId, currentUserEmail, request.getParentId(), request.getReplyToUserId(), request.getContent());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Bài viết không tồn tại: " + postId));

        User user = resolveUser(currentUserEmail);

        PostComment parent = null;
        if (request.getParentId() != null) {
            parent = postCommentRepository.findById(request.getParentId()).orElse(null);
            // If replying to a reply, flatten to root parent to maintain clean 1-level thread hierarchy
            if (parent != null && parent.getParent() != null) {
                parent = parent.getParent();
            }
        }

        User replyToUser = null;
        if (request.getReplyToUserId() != null) {
            replyToUser = userRepository.findById(request.getReplyToUserId()).orElse(null);
        } else if (parent != null) {
            replyToUser = parent.getAuthor();
        }

        PostComment comment = PostComment.builder()
                .post(post)
                .author(user)
                .parent(parent)
                .replyToUser(replyToUser)
                .content(request.getContent())
                .build();

        PostComment saved = postCommentRepository.save(comment);

        long totalComments = postCommentRepository.countByPostId(postId);
        post.setCommentCount((int) totalComments);
        postRepository.save(post);

        // Send notification to author or parent comment author
        try {
            if (replyToUser != null && !replyToUser.getId().equals(user.getId())) {
                notificationService.sendNotification(
                        replyToUser,
                        user,
                        "COMMENT_REPLY",
                        user.getFullName() + " đã trả lời bình luận của bạn trong bài viết '" + post.getTitle() + "'.",
                        "/community"
                );
            } else if (!post.getAuthor().getId().equals(user.getId())) {
                notificationService.sendNotification(
                        post.getAuthor(),
                        user,
                        "COMMENT",
                        user.getFullName() + " đã bình luận về bài viết của bạn: '" + (request.getContent().length() > 40 ? request.getContent().substring(0, 37) + "..." : request.getContent()) + "'",
                        "/community"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send notification for comment: {}", e.getMessage());
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

    @Transactional
    public PostDto updatePost(Long postId, UpdatePostRequest request, String currentUserEmail, HttpServletRequest httpRequest) {
        log.info("Updating post ID {} by user '{}': {}", postId, currentUserEmail, request.getTitle());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại với ID: " + postId));

        User currentUser = resolveUser(currentUserEmail);
        boolean isAdmin = currentUser.getRoles() != null && currentUser.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"));

        if (!post.getAuthor().getId().equals(currentUser.getId()) && !isAdmin) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa bài viết của người khác!");
        }

        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            title = request.getContent().length() > 50 ? request.getContent().substring(0, 47) + "..." : request.getContent();
        }

        // Re-evaluate with WanderAI Safety Shield
        AiContentModerationService.ModerationResult aiResult =
                aiContentModerationService.moderate(title, request.getContent(), request.getLocationTag(), request.getCategory(), post.getItinerary() != null);

        String status = aiResult.isApproved() ? "ACTIVE" : "PENDING_REVIEW";
        String postCategory = aiResult.isApproved()
                ? (request.getCategory() != null && !request.getCategory().isBlank() ? request.getCategory() : post.getCategory())
                : aiResult.getCategory();

        String imagesStr = null;
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            imagesStr = String.join(";;;", request.getImages());
        }

        String primaryImage = request.getImageUrl();
        if ((primaryImage == null || primaryImage.isBlank()) && request.getImages() != null && !request.getImages().isEmpty()) {
            primaryImage = request.getImages().get(0);
        }
        if (primaryImage == null || primaryImage.isBlank()) {
            primaryImage = post.getImageUrl();
        }

        post.setTitle(title);
        post.setContent(request.getContent());
        if (request.getLocationTag() != null) post.setLocationTag(request.getLocationTag());
        post.setImageUrl(primaryImage);
        post.setImages(imagesStr);
        if (request.getVideoUrl() != null) post.setVideoUrl(request.getVideoUrl());
        if (request.getVisibility() != null && !request.getVisibility().isBlank()) post.setVisibility(request.getVisibility());
        post.setCategory(postCategory);
        post.setStatus(status);
        post.setAiSafetyScore(aiResult.getSafetyScore());
        post.setAiFlagReason(aiResult.getFlagReason());
        post.setBadgeText(aiResult.getBadgeText());

        Post saved = postRepository.save(post);

        // Audit log
        String ip = activityLogService.extractClientIp(httpRequest);
        String ua = httpRequest != null && httpRequest.getHeader("User-Agent") != null ? httpRequest.getHeader("User-Agent") : "Web Client";
        activityLogService.recordLog(currentUser, "UPDATE_POST", "Chỉnh sửa bài viết ID #" + saved.getId() + ": '" + saved.getTitle() + "' (Trạng thái: " + status + ")", ip, ua);

        // Send notifications if re-flagged
        if (!aiResult.isApproved()) {
            try {
                notificationService.sendNotification(
                        post.getAuthor(),
                        null,
                        "SYSTEM",
                        "Bài viết '" + saved.getTitle() + "' sau khi chỉnh sửa đã chuyển sang CHỜ DUYỆT THỦ CÔNG do AI phát hiện cảnh báo: " + aiResult.getFlagReason(),
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to send re-moderation notice: {}", e.getMessage());
            }
        }

        return mapToDto(saved, currentUser);
    }

    @Transactional
    public PostDto updateVisibility(Long postId, String visibility, String currentUserEmail) {
        log.info("Updating visibility for post ID {} to '{}' by {}", postId, visibility, currentUserEmail);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại: " + postId));

        User currentUser = resolveUser(currentUserEmail);
        boolean isAdmin = currentUser.getRoles() != null && currentUser.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"));

        if (!post.getAuthor().getId().equals(currentUser.getId()) && !isAdmin) {
            throw new RuntimeException("Bạn không có quyền thay đổi chế độ bài viết này!");
        }

        post.setVisibility(visibility != null && !visibility.isBlank() ? visibility.toUpperCase() : "PUBLIC");
        Post saved = postRepository.save(post);
        return mapToDto(saved, currentUser);
    }

    @Transactional
    public void deletePost(Long postId, String currentUserEmail) {
        log.info("Deleting post ID {} by user {}", postId, currentUserEmail);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại: " + postId));

        User currentUser = resolveUser(currentUserEmail);
        boolean isAdmin = currentUser.getRoles() != null && currentUser.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"));

        if (!post.getAuthor().getId().equals(currentUser.getId()) && !isAdmin) {
            throw new RuntimeException("Bạn không có quyền xóa bài viết này!");
        }

        postRepository.delete(post);
    }

    public PostDto mapToDto(Post p, User currentUser) {
        boolean liked = false;
        boolean bookmarked = false;
        if (currentUser != null) {
            liked = postLikeRepository.existsByUserIdAndPostId(currentUser.getId(), p.getId());
            bookmarked = postBookmarkRepository.existsByUserIdAndPostId(currentUser.getId(), p.getId());
        }

        Itinerary itin = p.getItinerary();
        List<String> images = new ArrayList<>();
        if (p.getImages() != null && !p.getImages().isBlank()) {
            for (String img : p.getImages().split(";;;")) {
                if (!img.isBlank()) images.add(img.trim());
            }
        } else if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
            images.add(p.getImageUrl());
        }

        String formattedDate = p.getCreatedAt() != null
                ? p.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy"))
                : "Vừa xong";

        boolean isOwner = currentUser != null && p.getAuthor() != null && currentUser.getId().equals(p.getAuthor().getId());

        return PostDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .content(p.getContent())
                .locationTag(p.getLocationTag())
                .imageUrl(p.getImageUrl())
                .images(images)
                .videoUrl(p.getVideoUrl())
                .visibility(p.getVisibility() != null ? p.getVisibility() : "PUBLIC")
                .formattedDate(formattedDate)
                .isOwner(isOwner)
                .likeCount(p.getLikeCount() != null ? p.getLikeCount() : 0)
                .commentCount(p.getCommentCount() != null ? p.getCommentCount() : 0)
                .isLiked(liked)
                .isBookmarked(bookmarked)
                .category(p.getCategory())
                .status(p.getStatus())
                .aiSafetyScore(p.getAiSafetyScore())
                .aiFlagReason(p.getAiFlagReason())
                .badgeText(p.getBadgeText())
                .createdAt(p.getCreatedAt())
                .timeAgo(formatTimeAgo(p.getCreatedAt()))
                .authorId(p.getAuthor() != null ? p.getAuthor().getId() : null)
                .authorName(p.getAuthor() != null ? p.getAuthor().getFullName() : "Du khách Wayfare")
                .authorEmail(p.getAuthor() != null ? p.getAuthor().getEmail() : null)
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
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .replyToUserId(c.getReplyToUser() != null ? c.getReplyToUser().getId() : null)
                .replyToUserName(c.getReplyToUser() != null ? c.getReplyToUser().getFullName() : null)
                .replyToUserHandle(c.getReplyToUser() != null ? c.getReplyToUser().getHandle() : null)
                .replies(new ArrayList<>())
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
