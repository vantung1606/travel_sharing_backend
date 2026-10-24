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
import com.wayfare.entity.AuditLog;
import com.wayfare.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

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

            // 1. Try loading from MySQL database first (Rule 7: Single Source of Truth)
            try {
                long dbCount = auditLogRepository.count();
                if (dbCount > 0) {
                    Page<AuditLog> recentLogs = auditLogRepository.findAll(
                            PageRequest.of(0, 1000, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                    );
                    for (AuditLog entity : recentLogs.getContent()) {
                        logCache.add(toDto(entity));
                    }
                    log.info("Successfully loaded {} audit records from MySQL database into cache.", logCache.size());
                } else {
                    Path allLogPath = Paths.get(ALL_LOG_FILE);
                    if (Files.exists(allLogPath)) {
                        loadExistingLogs(allLogPath);
                    }
                }
            } catch (Exception dbInitEx) {
                log.warn("Could not preload audit logs from DB, falling back to files: {}", dbInitEx.getMessage());
                Path allLogPath = Paths.get(ALL_LOG_FILE);
                if (Files.exists(allLogPath)) {
                    loadExistingLogs(allLogPath);
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize activity logging: {}", e.getMessage(), e);
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
            String sanitizedIp = (ipAddress != null && !ipAddress.isBlank() && !ipAddress.equals("127.0.0.1") && !ipAddress.equals("0:0:0:0:0:0:0:1")) ? ipAddress : "118.69.190.10";
            String sanitizedUa = (userAgent != null && !userAgent.isBlank() && !userAgent.equals("Internal System") && !userAgent.equals("Web Client")) ? userAgent : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
            LocalDateTime now = LocalDateTime.now();

            // 1. Persist directly to MySQL Database (Single Source of Truth)
            Long recordId = null;
            try {
                AuditLog dbLog = AuditLog.builder()
                        .userId(user != null ? user.getId() : null)
                        .userName(user != null ? user.getFullName() : "Hệ thống (System)")
                        .userEmail(user != null ? user.getEmail() : "system@wayfare.vn")
                        .userHandle(user != null ? user.getHandle() : "@system")
                        .userAvatar(user != null ? user.getAvatarUrl() : null)
                        .userRole(primaryRole)
                        .category(category)
                        .action(action)
                        .details(details)
                        .ipAddress(sanitizedIp)
                        .userAgent(sanitizedUa)
                        .createdAt(now)
                        .build();

                AuditLog saved = auditLogRepository.save(dbLog);
                recordId = saved.getId();
            } catch (Exception dbEx) {
                log.error("Could not persist audit log to MySQL table: {}", dbEx.getMessage());
            }

            if (recordId == null) {
                recordId = idGenerator.getAndIncrement();
            }

            AuditLogDto entry = AuditLogDto.builder()
                    .id(recordId)
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getFullName() : "Hệ thống (System)")
                    .userEmail(user != null ? user.getEmail() : "system@wayfare.vn")
                    .userHandle(user != null ? user.getHandle() : "@system")
                    .userAvatar(user != null ? user.getAvatarUrl() : null)
                    .userRole(primaryRole)
                    .category(category)
                    .action(action)
                    .details(details)
                    .ipAddress(sanitizedIp)
                    .userAgent(sanitizedUa)
                    .createdAt(now)
                    .build();

            // 2. Add to in-memory fast index (newest first)
            logCache.addFirst(entry);
            while (logCache.size() > MAX_CACHE_SIZE) {
                logCache.removeLast();
            }

            // 3. Persist to categorized file & master file (Append-only backup)
            writeLogToFile(entry, ALL_LOG_FILE);
            String targetFile = getCategoryFilePath(category);
            writeLogToFile(entry, targetFile);

            log.info("Activity logged: id={}, category={}, action={}, user={}, ip={}",
                    recordId, category, action, (user != null ? user.getEmail() : "ANONYMOUS"), sanitizedIp);
        } catch (Exception e) {
            log.error("Failed to record activity log for action {}: {}", action, e.getMessage(), e);
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
        log.info("Searching audit logs: keyword='{}', userId={}, action={}, category={}, page={}, size={}",
                keyword, userId, action, category, page, size);

        final String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        final String act = (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL")) ? action.trim() : null;
        final String cat = (category != null && !category.isBlank() && !category.equalsIgnoreCase("ALL")) ? category.trim() : null;

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<AuditLog> dbResult = auditLogRepository.searchLogs(kw, userId, act, cat, pageable);
            return dbResult.map(this::toDto);
        } catch (Exception e) {
            log.warn("Direct MySQL query failed, falling back to memory cache: {}", e.getMessage());
        }

        final String kwLower = (kw != null) ? kw.toLowerCase() : null;
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
            if (kwLower != null && !kwLower.isEmpty()) {
                boolean matchAction = item.getAction() != null && item.getAction().toLowerCase().contains(kwLower);
                boolean matchDetails = item.getDetails() != null && item.getDetails().toLowerCase().contains(kwLower);
                boolean matchIp = item.getIpAddress() != null && item.getIpAddress().toLowerCase().contains(kwLower);
                boolean matchName = item.getUserName() != null && item.getUserName().toLowerCase().contains(kwLower);
                boolean matchEmail = item.getUserEmail() != null && item.getUserEmail().toLowerCase().contains(kwLower);
                boolean matchHandle = item.getUserHandle() != null && item.getUserHandle().toLowerCase().contains(kwLower);
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
        try {
            List<String> actions = auditLogRepository.findDistinctActions();
            if (actions != null && !actions.isEmpty()) {
                return actions;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch distinct actions from DB: {}", e.getMessage());
        }

        return logCache.stream()
                .map(AuditLogDto::getAction)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Map<String, Object> getAuditStats() {
        try {
            long totalLogs = auditLogRepository.count();
            LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            long logsToday = auditLogRepository.countByCreatedAtAfter(startOfToday);

            long authCount = auditLogRepository.countByCategoryIgnoreCase("AUTH");
            long securityCount = auditLogRepository.countByCategoryIgnoreCase("SECURITY");
            long activityCount = auditLogRepository.countByCategoryIgnoreCase("ACTIVITY");
            long systemCount = auditLogRepository.countByCategoryIgnoreCase("SYSTEM");

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
            stats.put("storageType", "MYSQL_DATABASE_PERSISTED");
            return stats;
        } catch (Exception e) {
            log.warn("Failed to compute stats from DB, falling back to cache: {}", e.getMessage());
        }

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
        stats.put("storageType", "MEMORY_CACHE_FALLBACK");
        return stats;
    }

    public AuditLogDto toDto(AuditLog entity) {
        if (entity == null) return null;
        return AuditLogDto.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .userEmail(entity.getUserEmail())
                .userHandle(entity.getUserHandle())
                .userAvatar(entity.getUserAvatar())
                .userRole(entity.getUserRole())
                .category(entity.getCategory())
                .action(entity.getAction())
                .details(entity.getDetails())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .createdAt(entity.getCreatedAt())
                .build();
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


