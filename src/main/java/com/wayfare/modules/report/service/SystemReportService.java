package com.wayfare.modules.report.service;

import com.wayfare.entity.*;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.admin.service.ActivityLogService;
import com.wayfare.modules.notification.service.NotificationService;
import com.wayfare.modules.report.dto.CreateReportRequest;
import com.wayfare.modules.report.dto.ReportDto;
import com.wayfare.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemReportService {

    private final SystemReportRepository systemReportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PlaceRepository placeRepository;
    private final ReviewRepository reviewRepository;
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

        switch (targetType) {
            case "POST" -> {
                Post post = postRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại với ID: " + request.getTargetId()));
                targetTitle = post.getTitle() != null ? post.getTitle() : "Bài viết #" + post.getId();
            }
            case "PLACE" -> {
                Place place = placeRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Địa điểm không tồn tại với ID: " + request.getTargetId()));
                targetTitle = place.getName() != null ? place.getName() : "Địa điểm #" + place.getId();
            }
            case "USER" -> {
                User targetUser = userRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Người dùng bị báo cáo không tồn tại"));
                targetTitle = targetUser.getFullName() + " (" + targetUser.getEmail() + ")";
            }
            case "REVIEW" -> {
                Review review = reviewRepository.findById(request.getTargetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Đánh giá không tồn tại với ID: " + request.getTargetId()));
                targetTitle = "Đánh giá tại " + (review.getPlace() != null ? review.getPlace().getName() : "địa điểm");
            }
            default -> log.warn("Unknown targetType in report: {}", targetType);
        }

        SystemReport report = SystemReport.builder()
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

        SystemReport saved = systemReportRepository.save(report);
        log.info("Saved SystemReport with ID: {}", saved.getId());

        // Notify Admins
        try {
            String notifMsg = String.format("Báo cáo vi phạm mới [%s]: %s - Lý do: %s",
                    targetType, targetTitle, request.getReason());
            notificationService.notifyAdmins("REPORT_ALERT", notifMsg, "/admin/reports");
        } catch (Exception e) {
            log.warn("Failed to notify admins of system report: {}", e.getMessage());
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
        log.info("Fetching system reports with status={}, targetType={}", status, targetType);
        List<SystemReport> reports;

        if (status != null && !status.equalsIgnoreCase("ALL")) {
            reports = systemReportRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else if (targetType != null && !targetType.equalsIgnoreCase("ALL")) {
            reports = systemReportRepository.findByTargetTypeOrderByCreatedAtDesc(targetType.toUpperCase());
        } else {
            reports = systemReportRepository.findAllByOrderByCreatedAtDesc();
        }

        return reports.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ReportDto resolveReport(Long reportId, String action, String notes, String adminEmail) {
        log.info("Admin {} resolving report ID {} with action={}, notes={}", adminEmail, reportId, action, notes);

        SystemReport report = systemReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại với ID: " + reportId));

        report.setStatus("RESOLVED");
        report.setActionTaken(action != null ? action.toUpperCase().trim() : "RESOLVED");
        report.setModeratorNotes(notes != null ? notes.trim() : null);

        SystemReport updated = systemReportRepository.save(report);

        try {
            User admin = userRepository.findByEmail(adminEmail).orElse(null);
            activityLogService.recordLog(
                    admin,
                    "ADMIN_RESOLVE_REPORT",
                    "Quản trị viên " + adminEmail + " đã xử lý báo cáo #" + reportId + " (Hành động: " + action + ")"
            );
        } catch (Exception ignored) {}

        return toDto(updated);
    }

    private ReportDto toDto(SystemReport r) {
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
                .status(r.getStatus())
                .moderatorNotes(r.getModeratorNotes())
                .actionTaken(r.getActionTaken())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
