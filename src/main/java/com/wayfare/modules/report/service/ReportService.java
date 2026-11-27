package com.wayfare.modules.report.service;

import com.wayfare.entity.*;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.admin.service.ActivityLogService;
import com.wayfare.modules.notification.service.NotificationService;
import com.wayfare.modules.place.service.ReviewService;
import com.wayfare.modules.report.dto.CreateReportRequest;
import com.wayfare.modules.report.dto.ReportDto;
import com.wayfare.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PlaceRepository placeRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @Transactional
    public ReportDto submitReport(CreateReportRequest request, String reporterEmail) {
        log.info("Submitting report for targetType={} targetId={} by user={}",
                request.getTargetType(), request.getTargetId(), reporterEmail);

        User reporter = userRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng báo cáo không tồn tại"));

        String targetType = request.getTargetType().toUpperCase().trim();
        String targetTitle = "ID: " + request.getTargetId();

        // Chặn spam: không cho gửi trùng lặp khi đã có báo cáo PENDING cho cùng đối tượng
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporter.getId(), targetType, request.getTargetId(), "PENDING")) {
            throw new IllegalArgumentException("Bạn đã gửi báo cáo cho nội dung này rồi và đang chờ quản trị viên xem xét.");
        }

        switch (targetType) {
            case "POST" -> {
                Post post = postRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại với ID: " + request.getTargetId()));
                if (post.getAuthor() != null && post.getAuthor().getId().equals(reporter.getId())) {
                    throw new IllegalArgumentException("Bạn không thể tự báo cáo bài viết của chính mình.");
                }
                targetTitle = post.getTitle() != null ? post.getTitle() : "Bài viết #" + post.getId();
                // Update post report counter and status
                int count = post.getReportsCount() != null ? post.getReportsCount() : 0;
                post.setReportsCount(count + 1);
                post.setStatus("PENDING_REPORT");
                post.setReportReason(request.getReason());
                post.setBadgeText((count + 1) + " Lượt báo cáo");
                post.setFlagSource("USER");
                post.setUpdatedAt(LocalDateTime.now());
                postRepository.save(post);
            }
            case "PLACE" -> {
                Place place = placeRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Địa điểm không tồn tại với ID: " + request.getTargetId()));
                targetTitle = place.getName() != null ? place.getName() : "Địa điểm #" + place.getId();
            }
            case "USER" -> {
                User targetUser = userRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Người dùng bị báo cáo không tồn tại"));
                if (targetUser.getId().equals(reporter.getId())) {
                    throw new IllegalArgumentException("Bạn không thể tự báo cáo tài khoản của chính mình.");
                }
                targetTitle = targetUser.getFullName() + " (" + targetUser.getEmail() + ")";
            }
            case "REVIEW" -> {
                Review review = reviewRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Đánh giá không tồn tại với ID: " + request.getTargetId()));
                if (review.getAuthor() != null && review.getAuthor().getId().equals(reporter.getId())) {
                    throw new IllegalArgumentException("Bạn không thể tự báo cáo đánh giá của chính mình.");
                }
                String placeName = review.getPlace() != null ? review.getPlace().getName() : "địa điểm";
                String commentSnippet = review.getComment() != null ? review.getComment().trim().replaceAll("\\s+", " ") : "";
                if (commentSnippet.length() > 90) {
                    commentSnippet = commentSnippet.substring(0, 87) + "...";
                }
                targetTitle = commentSnippet.isEmpty()
                        ? "Đánh giá tại " + placeName
                        : "Đánh giá tại " + placeName + ": \"" + commentSnippet + "\"";
                if (targetTitle.length() > 240) {
                    targetTitle = targetTitle.substring(0, 237) + "...";
                }
            }
            default -> log.warn("Unknown targetType in report: {}", targetType);
        }

        Report report = Report.builder()
                .targetType(targetType)
                .targetId(request.getTargetId())
                .targetTitle(targetTitle)
                .reporter(reporter)
                .category(request.getCategory().trim())
                .reason(request.getReason().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .evidenceImageUrl(request.getEvidenceImageUrl() != null && !request.getEvidenceImageUrl().isBlank() ? request.getEvidenceImageUrl().trim() : null)
                .status("PENDING")
                .build();

        Report saved = reportRepository.save(report);
        log.info("Saved Report into table 'reports' with ID: {}", saved.getId());

        // Notify Admins
        try {
            String notifMsg = String.format("Báo cáo vi phạm mới [%s]: %s - Lý do: %s",
                    targetType, targetTitle, request.getReason());
            notificationService.notifyAdmins("REPORT_ALERT", notifMsg, "/admin/reports");
        } catch (Exception e) {
            log.warn("Failed to notify admins of report: {}", e.getMessage());
        }

        // Record Audit log
        try {
            activityLogService.recordLog(
                    reporter,
                    "USER_SUBMIT_REPORT",
                    "Thành viên " + reporter.getFullName() + " gửi báo cáo [" + targetType + " #" + request.getTargetId() + "]: " + request.getReason()
            );
        } catch (Exception ignored) {}

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportDto> getReports(String status, String targetType) {
        log.info("Fetching reports from table 'reports' with status={}, targetType={}", status, targetType);
        List<Report> reports;

        boolean hasStatus = status != null && !status.equalsIgnoreCase("ALL") && !status.isBlank();
        boolean hasType = targetType != null && !targetType.equalsIgnoreCase("ALL") && !targetType.isBlank();

        if (hasStatus && hasType) {
            reports = reportRepository.findByTargetTypeAndStatusOrderByCreatedAtDesc(targetType.toUpperCase(), status.toUpperCase());
        } else if (hasStatus) {
            reports = reportRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else if (hasType) {
            reports = reportRepository.findByTargetTypeOrderByCreatedAtDesc(targetType.toUpperCase());
        } else {
            reports = reportRepository.findAllByOrderByCreatedAtDesc();
        }

        return reports.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ReportDto resolveReport(Long reportId, String action, String notes, String adminEmail) {
        log.info("Admin {} resolving report ID {} with action={}, notes={}", adminEmail, reportId, action, notes);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại với ID: " + reportId));

        User admin = userRepository.findByEmail(adminEmail).orElse(null);
        String act = action != null ? action.toUpperCase().trim() : "RESOLVED";
        String targetType = report.getTargetType() != null ? report.getTargetType().toUpperCase().trim() : "";
        Long targetId = report.getTargetId();
        String enforcementSummary = act;

        if ("DISMISS".equalsIgnoreCase(act) || "DISMISSED".equalsIgnoreCase(act)) {
            report.setStatus("DISMISSED");
            enforcementSummary = "Bác bỏ báo cáo";
            if ("POST".equals(targetType) && targetId != null) {
                postRepository.findById(targetId).ifPresent(p -> {
                    if ("PENDING_REPORT".equalsIgnoreCase(p.getStatus())) {
                        p.setStatus("ACTIVE");
                        postRepository.save(p);
                    }
                });
            }
        } else {
            report.setStatus("RESOLVED");
            boolean isHide = "HIDE_TARGET".equalsIgnoreCase(act) || "RESOLVE".equalsIgnoreCase(act) || "RESOLVED".equalsIgnoreCase(act);
            boolean isDelete = "DELETE_TARGET".equalsIgnoreCase(act);

            if (targetId != null) {
                switch (targetType) {
                    case "REVIEW" -> {
                        if (isDelete) {
                            reviewRepository.findById(targetId).ifPresent(r -> {
                                User reviewAuthor = r.getAuthor();
                                reviewService.deleteReviewByModerator(targetId);
                                if (reviewAuthor != null) {
                                    notificationService.sendNotification(
                                            reviewAuthor, admin, "MODERATION_ALERT",
                                            "Đánh giá của bạn tại \"" + (r.getPlace() != null ? r.getPlace().getName() : "địa điểm") + "\" đã bị xóa do vi phạm tiêu chuẩn cộng đồng.",
                                            "/explore"
                                    );
                                }
                            });
                            enforcementSummary = "Đã xóa đánh giá vi phạm";
                        } else if (isHide) {
                            reviewRepository.findById(targetId).ifPresent(r -> {
                                reviewService.setReviewHidden(targetId, true);
                                if (r.getAuthor() != null) {
                                    notificationService.sendNotification(
                                            r.getAuthor(), admin, "MODERATION_ALERT",
                                            "Đánh giá của bạn tại \"" + (r.getPlace() != null ? r.getPlace().getName() : "địa điểm") + "\" đã bị ẩn do vi phạm tiêu chuẩn cộng đồng.",
                                            "/explore"
                                    );
                                }
                            });
                            enforcementSummary = "Đã ẩn đánh giá vi phạm";
                        }
                    }
                    case "POST" -> {
                        postRepository.findById(targetId).ifPresent(p -> {
                            p.setStatus(isDelete ? "REMOVED" : "HIDDEN");
                            p.setUpdatedAt(LocalDateTime.now());
                            postRepository.save(p);
                            if (p.getAuthor() != null) {
                                notificationService.sendNotification(
                                        p.getAuthor(), admin, "MODERATION_ALERT",
                                        "Bài viết \"" + (p.getTitle() != null ? p.getTitle() : "#" + p.getId()) + "\" đã bị " + (isDelete ? "gỡ bỏ" : "ẩn") + " do báo cáo vi phạm.",
                                        "/community"
                                );
                            }
                        });
                        enforcementSummary = isDelete ? "Đã gỡ bài viết" : "Đã ẩn bài viết";
                    }
                    case "PLACE" -> {
                        placeRepository.findById(targetId).ifPresent(pl -> {
                            pl.setStatus(isDelete ? "REJECTED" : "HIDDEN");
                            placeRepository.save(pl);
                        });
                        enforcementSummary = isDelete ? "Đã gỡ địa điểm" : "Đã ẩn địa điểm";
                    }
                    case "USER" -> {
                        User targetUser = userRepository.findById(targetId).orElse(null);
                        if (targetUser != null) {
                            boolean isTargetAdmin = targetUser.getRoles() != null && targetUser.getRoles().stream()
                                    .anyMatch(r -> r.getName() != null && r.getName().toUpperCase().contains("ADMIN"));
                            if (isTargetAdmin || (admin != null && targetUser.getId().equals(admin.getId()))) {
                                throw new IllegalArgumentException("Không thể khóa tài khoản Quản trị viên hoặc chính bạn!");
                            }
                            targetUser.setStatus("LOCKED");
                            targetUser.setIsLocked(true);
                            userRepository.save(targetUser);
                        }
                        enforcementSummary = "Đã khóa tài khoản vi phạm";
                    }
                    default -> log.warn("No enforcement handler for targetType={}", targetType);
                }
            }
        }

        report.setActionTaken(act);
        report.setModeratorNotes(notes != null && !notes.isBlank() ? notes.trim() : enforcementSummary);
        report.setResolvedBy(admin);
        report.setResolvedAt(LocalDateTime.now());

        Report updated = reportRepository.save(report);

        // Thông báo lại cho người gửi báo cáo
        try {
            if (report.getReporter() != null) {
                String reporterMsg = "DISMISSED".equals(report.getStatus())
                        ? "Báo cáo #" + reportId + " của bạn đã được xem xét nhưng chưa đủ căn cứ vi phạm."
                        : "Báo cáo #" + reportId + " của bạn đã được xử lý (" + enforcementSummary + "). Cảm ơn bạn đã đóng góp cho cộng đồng Wayfare!";
                notificationService.sendNotification(report.getReporter(), admin, "REPORT_RESULT", reporterMsg, "/explore");
            }
        } catch (Exception e) {
            log.warn("Failed to notify reporter for report {}: {}", reportId, e.getMessage());
        }

        try {
            activityLogService.recordLog(
                    admin,
                    "ADMIN_RESOLVE_REPORT",
                    "Quản trị viên " + adminEmail + " đã xử lý báo cáo #" + reportId + " [" + targetType + " #" + targetId + "] (" + enforcementSummary + ")"
            );
        } catch (Exception ignored) {}

        return toDto(updated);
    }

    @Transactional
    public void deleteReport(Long reportId, String adminEmail) {
        log.info("Admin {} deleting report ID {}", adminEmail, reportId);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại với ID: " + reportId));
        reportRepository.delete(report);
    }

    public ReportDto toDto(Report r) {
        return ReportDto.builder()
                .id(r.getId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .targetTitle(r.getTargetTitle())
                .reporterId(r.getReporter() != null ? r.getReporter().getId() : null)
                .reporterName(r.getReporter() != null ? r.getReporter().getFullName() : "Ẩn danh")
                .reporterEmail(r.getReporter() != null ? r.getReporter().getEmail() : "")
                .reporterAvatar(r.getReporter() != null ? r.getReporter().getAvatarUrl() : null)
                .category(r.getCategory())
                .reason(r.getReason())
                .description(r.getDescription())
                .evidenceImageUrl(r.getEvidenceImageUrl())
                .aiAnalysisSnippet(r.getAiAnalysisSnippet())
                .status(r.getStatus())
                .moderatorNotes(r.getModeratorNotes())
                .actionTaken(r.getActionTaken())
                .resolvedByName(r.getResolvedBy() != null ? r.getResolvedBy().getFullName() : null)
                .resolvedAt(r.getResolvedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
