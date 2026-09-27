package com.wayfare.service;

import com.wayfare.dto.AuditLogDto;
import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import com.wayfare.entity.UserActivityLog;
import com.wayfare.repository.UserActivityLogRepository;
import com.wayfare.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final UserActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordLog(User user, String action, String details, String ipAddress, String userAgent) {
        try {
            UserActivityLog logEntry = UserActivityLog.builder()
                    .user(user)
                    .action(action)
                    .details(details)
                    .ipAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : "127.0.0.1")
                    .userAgent(userAgent != null && !userAgent.isBlank() ? userAgent : "Internal System")
                    .build();

            activityLogRepository.save(logEntry);
            log.info("System activity logged: action={}, user={}, ip={}",
                    action, (user != null ? user.getEmail() : "ANONYMOUS"), ipAddress);
        } catch (Exception e) {
            log.error("Failed to record activity log for action {}: {}", action, e.getMessage(), e);
        }
    }

    @Transactional
    public void recordLog(Long userId, String action, String details, HttpServletRequest request) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        String ip = extractClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";
        recordLog(user, action, details, ip, userAgent);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> searchLogs(String keyword, Long userId, String action, int page, int size) {
        log.info("Searching audit logs with keyword='{}', userId={}, action={}, page={}, size={}",
                keyword, userId, action, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserActivityLog> logPage = activityLogRepository.searchLogs(keyword, userId, action, pageable);
        return logPage.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableActions() {
        return activityLogRepository.findDistinctActions();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAuditStats() {
        long totalLogs = activityLogRepository.count();
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long logsToday = activityLogRepository.countByCreatedAtAfter(startOfToday);
        List<String> actions = activityLogRepository.findDistinctActions();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", totalLogs);
        stats.put("logsToday", logsToday);
        stats.put("distinctActionsCount", actions.size());
        stats.put("availableActions", actions);
        return stats;
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return (ip != null && !ip.isBlank()) ? ip : "127.0.0.1";
    }

    private AuditLogDto mapToDto(UserActivityLog logEntity) {
        User u = logEntity.getUser();
        String primaryRole = "MEMBER";
        if (u != null && u.getRoles() != null && !u.getRoles().isEmpty()) {
            primaryRole = u.getRoles().stream()
                    .map(Role::getName)
                    .filter(r -> r.contains("ADMIN"))
                    .findFirst()
                    .orElse(u.getRoles().iterator().next().getName());
        }

        return AuditLogDto.builder()
                .id(logEntity.getId())
                .userId(u != null ? u.getId() : null)
                .userName(u != null ? u.getFullName() : "Hệ thống (System)")
                .userEmail(u != null ? u.getEmail() : "system@wayfare.vn")
                .userHandle(u != null ? u.getHandle() : "@system")
                .userAvatar(u != null ? u.getAvatarUrl() : null)
                .userRole(primaryRole)
                .action(logEntity.getAction())
                .details(logEntity.getDetails())
                .ipAddress(logEntity.getIpAddress())
                .userAgent(logEntity.getUserAgent())
                .createdAt(logEntity.getCreatedAt())
                .build();
    }
}
