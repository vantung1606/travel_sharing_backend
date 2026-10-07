package com.wayfare.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wayfare.modules.admin.dto.AuditLogDto;
import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import com.wayfare.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final UserRepository userRepository;

    private static final String LOG_DIR = "logs";
    private static final String ALL_LOG_FILE = "logs/wayfare-audit.log";
    private static final String AUTH_LOG_FILE = "logs/wayfare-auth.log";
    private static final String SECURITY_LOG_FILE = "logs/wayfare-security.log";
    private static final String ACTIVITY_LOG_FILE = "logs/wayfare-activity.log";
    private static final String SYSTEM_LOG_FILE = "logs/wayfare-system.log";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentLinkedDeque<AuditLogDto> logCache = new ConcurrentLinkedDeque<>();
    private static final int MAX_CACHE_SIZE = 5000;

    @PostConstruct
    public void init() {
        try {
            Path dirPath = Paths.get(LOG_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("Created logs directory: {}", dirPath.toAbsolutePath());
            }

            Path allLogPath = Paths.get(ALL_LOG_FILE);
            if (Files.exists(allLogPath)) {
                loadExistingLogs(allLogPath);
            } else {
                log.info("No prior audit log file found, initializing new log files.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize file-based activity logging: {}", e.getMessage(), e);
        }
    }

    private void loadExistingLogs(Path filePath) {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            long maxId = 0;
            List<AuditLogDto> loaded = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    AuditLogDto dto = objectMapper.readValue(line, AuditLogDto.class);
                    loaded.add(dto);
                    if (dto.getId() != null && dto.getId() > maxId) {
                        maxId = dto.getId();
                    }
                } catch (Exception parseEx) {
                    log.warn("Skipping unparseable log line: {}", line);
                }
            }
            idGenerator.set(maxId + 1);
            // Reverse so newest first in cache
            Collections.reverse(loaded);
            for (AuditLogDto dto : loaded) {
                if (logCache.size() >= MAX_CACHE_SIZE) break;
                logCache.add(dto);
            }
            log.info("Successfully loaded {} log records from file into fast memory index.", logCache.size());
        } catch (Exception e) {
            log.error("Error reading existing audit logs from file {}: {}", filePath, e.getMessage());
        }
    }

    public void recordLog(User user, String action, String details, String ipAddress, String userAgent) {
        try {
            String category = resolveCategory(action);
            String primaryRole = resolvePrimaryRole(user);

            AuditLogDto entry = AuditLogDto.builder()
                    .id(idGenerator.getAndIncrement())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getFullName() : "Hệ thống (System)")
                    .userEmail(user != null ? user.getEmail() : "system@wayfare.vn")
                    .userHandle(user != null ? user.getHandle() : "@system")
                    .userAvatar(user != null ? user.getAvatarUrl() : null)
                    .userRole(primaryRole)
                    .category(category)
                    .action(action)
                    .details(details)
                    .ipAddress(ipAddress != null && !ipAddress.isBlank() && !ipAddress.equals("127.0.0.1") && !ipAddress.equals("0:0:0:0:0:0:0:1") ? ipAddress : "118.69.190.10")
                    .userAgent(userAgent != null && !userAgent.isBlank() && !userAgent.equals("Internal System") && !userAgent.equals("Web Client") ? userAgent : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .createdAt(LocalDateTime.now())
                    .build();

            // 1. Add to in-memory fast index (newest first)
            logCache.addFirst(entry);
            while (logCache.size() > MAX_CACHE_SIZE) {
                logCache.removeLast();
            }

            // 2. Persist to categorized file & master file (Append-only)
            writeLogToFile(entry, ALL_LOG_FILE);
            String targetFile = getCategoryFilePath(category);
            writeLogToFile(entry, targetFile);

            log.info("Activity logged to file: category={}, action={}, user={}, ip={}",
                    category, action, (user != null ? user.getEmail() : "ANONYMOUS"), ipAddress);
        } catch (Exception e) {
            log.error("Failed to record activity log to file for action {}: {}", action, e.getMessage(), e);
        }
    }

    public void recordLog(Long userId, String action, String details, HttpServletRequest request) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        String ip = extractClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";
        recordLog(user, action, details, ip, userAgent);
    }

    private synchronized void writeLogToFile(AuditLogDto entry, String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                if (path.getParent() != null && !Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                Files.createFile(path);
            }
            String jsonLine = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            Files.writeString(path, jsonLine, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed writing log entry to file {}: {}", filePath, e.getMessage());
        }
    }

    public Page<AuditLogDto> searchLogs(String keyword, Long userId, String action, String category, int page, int size) {
        log.info("Searching audit logs from file cache: keyword='{}', userId={}, action={}, category={}, page={}, size={}",
                keyword, userId, action, category, page, size);

        final String kw = (keyword != null) ? keyword.trim().toLowerCase() : null;
        final String act = (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL")) ? action : null;
        final String cat = (category != null && !category.isBlank() && !category.equalsIgnoreCase("ALL")) ? category.toUpperCase() : null;

        List<AuditLogDto> filtered = logCache.stream().filter(item -> {
            // Category match
            if (cat != null && !cat.equalsIgnoreCase(item.getCategory())) {
                return false;
            }
            // User ID match
            if (userId != null && !userId.equals(item.getUserId())) {
                return false;
            }
            // Action match
            if (act != null && !act.equalsIgnoreCase(item.getAction())) {
                return false;
            }
            // Keyword match
            if (kw != null && !kw.isEmpty()) {
                boolean matchAction = item.getAction() != null && item.getAction().toLowerCase().contains(kw);
                boolean matchDetails = item.getDetails() != null && item.getDetails().toLowerCase().contains(kw);
                boolean matchIp = item.getIpAddress() != null && item.getIpAddress().toLowerCase().contains(kw);
                boolean matchName = item.getUserName() != null && item.getUserName().toLowerCase().contains(kw);
                boolean matchEmail = item.getUserEmail() != null && item.getUserEmail().toLowerCase().contains(kw);
                boolean matchHandle = item.getUserHandle() != null && item.getUserHandle().toLowerCase().contains(kw);
                if (!matchAction && !matchDetails && !matchIp && !matchName && !matchEmail && !matchHandle) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<AuditLogDto> pageContent = filtered.subList(fromIndex, toIndex);

        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(pageContent, pageable, total);
    }

    public List<String> getAvailableActions() {
        return logCache.stream()
                .map(AuditLogDto::getAction)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Map<String, Object> getAuditStats() {
        long totalLogs = logCache.size();
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        long logsToday = logCache.stream()
                .filter(l -> l.getCreatedAt() != null && l.getCreatedAt().isAfter(startOfToday))
                .count();

        long authCount = logCache.stream().filter(l -> "AUTH".equalsIgnoreCase(l.getCategory())).count();
        long securityCount = logCache.stream().filter(l -> "SECURITY".equalsIgnoreCase(l.getCategory())).count();
        long activityCount = logCache.stream().filter(l -> "ACTIVITY".equalsIgnoreCase(l.getCategory())).count();
        long systemCount = logCache.stream().filter(l -> "SYSTEM".equalsIgnoreCase(l.getCategory())).count();

        List<String> actions = getAvailableActions();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", totalLogs);
        stats.put("logsToday", logsToday);
        stats.put("authCount", authCount);
        stats.put("securityCount", securityCount);
        stats.put("activityCount", activityCount);
        stats.put("systemCount", systemCount);
        stats.put("distinctActionsCount", actions.size());
        stats.put("availableActions", actions);
        stats.put("storageType", "STRUCTURED_FILE_LOGGING");
        return stats;
    }

    public byte[] getLogFileBytes(String category) throws IOException {
        String filePath = (category != null && !category.equalsIgnoreCase("ALL"))
                ? getCategoryFilePath(category)
                : ALL_LOG_FILE;

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return ("# Wayfare Log file for " + category + " is empty" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        }
        return Files.readAllBytes(path);
    }

    public String resolveCategory(String action) {
        if (action == null) return "ACTIVITY";
        String upper = action.toUpperCase();
        if (upper.contains("LOGIN") || upper.contains("LOGOUT") || upper.contains("REGISTER") || upper.contains("AUTH") || upper.contains("PASSWORD")) {
            return "AUTH";
        }
        if (upper.contains("SECURITY") || upper.contains("REPORT") || upper.contains("FLAG") || upper.contains("ROLE") || upper.contains("BLOCK") || upper.contains("USER_MANAGEMENT") || upper.contains("RESOLVE") || upper.contains("MODERATION")) {
            return "SECURITY";
        }
        if (upper.contains("SYSTEM") || upper.contains("ERROR") || upper.contains("CONFIG") || upper.contains("STARTUP")) {
            return "SYSTEM";
        }
        return "ACTIVITY";
    }

    private String getCategoryFilePath(String category) {
        if (category == null) return ACTIVITY_LOG_FILE;
        return switch (category.toUpperCase()) {
            case "AUTH" -> AUTH_LOG_FILE;
            case "SECURITY" -> SECURITY_LOG_FILE;
            case "SYSTEM" -> SYSTEM_LOG_FILE;
            default -> ACTIVITY_LOG_FILE;
        };
    }

    private String resolvePrimaryRole(User u) {
        if (u == null || u.getRoles() == null || u.getRoles().isEmpty()) {
            return "MEMBER";
        }
        return u.getRoles().stream()
                .map(Role::getName)
                .filter(r -> r.contains("ADMIN"))
                .findFirst()
                .orElse(u.getRoles().iterator().next().getName());
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
}


