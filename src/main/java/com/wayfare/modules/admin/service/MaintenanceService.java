package com.wayfare.modules.admin.service;

import com.wayfare.entity.SiteSetting;
import com.wayfare.entity.User;
import com.wayfare.modules.admin.dto.MaintenanceDto;
import com.wayfare.repository.SiteSettingRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceService {

    private final SiteSettingRepository siteSettingRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    private static final String KEY_ENABLED = "maintenance.enabled";
    private static final String KEY_TITLE = "maintenance.title";
    private static final String KEY_MESSAGE = "maintenance.message";
    private static final String KEY_ESTIMATED_END = "maintenance.estimated_end_time";
    private static final String KEY_BYPASS = "maintenance.allow_admin_bypass";
    private static final String KEY_UPDATED_BY = "maintenance.updated_by";
    private static final String KEY_UPDATED_AT = "maintenance.updated_at";

    private String getSettingValue(String key, String defaultValue) {
        return siteSettingRepository.findBySettingKey(key)
                .map(SiteSetting::getSettingValue)
                .orElse(defaultValue);
    }

    private void saveSetting(String key, String value, String type, Long adminId) {
        SiteSetting setting = siteSettingRepository.findBySettingKey(key)
                .orElseGet(() -> SiteSetting.builder()
                        .settingKey(key)
                        .groupName("MAINTENANCE")
                        .isPublic(true)
                        .valueType(type)
                        .build());

        setting.setSettingValue(value != null ? value : "");
        setting.setValueType(type);
        setting.setUpdatedBy(adminId);
        setting.setGroupName("MAINTENANCE");
        setting.setIsPublic(true);
        siteSettingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public MaintenanceDto getMaintenanceStatus() {
        boolean enabled = Boolean.parseBoolean(getSettingValue(KEY_ENABLED, "false"));
        String title = getSettingValue(KEY_TITLE, "Hệ thống đang bảo trì nâng cấp");
        String message = getSettingValue(KEY_MESSAGE, "Wayfare đang thực hiện nâng cấp hệ thống định kỳ nhằm mang lại trải nghiệm khám phá du lịch mượt mà hơn. Rất mong bạn thông cảm và quay lại sau ít phút!");
        String estimatedEndTime = getSettingValue(KEY_ESTIMATED_END, null);
        boolean allowAdminBypass = Boolean.parseBoolean(getSettingValue(KEY_BYPASS, "true"));
        String updatedByEmail = getSettingValue(KEY_UPDATED_BY, null);
        String updatedAt = getSettingValue(KEY_UPDATED_AT, null);

        return MaintenanceDto.builder()
                .enabled(enabled)
                .title(title)
                .message(message)
                .estimatedEndTime(estimatedEndTime)
                .allowAdminBypass(allowAdminBypass)
                .updatedByEmail(updatedByEmail)
                .updatedAt(updatedAt)
                .build();
    }

    @Transactional
    public MaintenanceDto updateMaintenanceStatus(MaintenanceDto request, String adminEmail, String ip, String userAgent) {
        Long adminId = null;
        User admin = null;
        if (adminEmail != null && !adminEmail.isBlank()) {
            Optional<User> adminOpt = userRepository.findByEmail(adminEmail);
            if (adminOpt.isPresent()) {
                admin = adminOpt.get();
                adminId = admin.getId();
            }
        }

        boolean prevEnabled = Boolean.parseBoolean(getSettingValue(KEY_ENABLED, "false"));
        boolean newEnabled = request.getEnabled() != null ? request.getEnabled() : false;

        saveSetting(KEY_ENABLED, String.valueOf(newEnabled), "BOOLEAN", adminId);
        if (request.getTitle() != null) {
            saveSetting(KEY_TITLE, request.getTitle().trim(), "STRING", adminId);
        }
        if (request.getMessage() != null) {
            saveSetting(KEY_MESSAGE, request.getMessage().trim(), "STRING", adminId);
        }
        saveSetting(KEY_ESTIMATED_END, request.getEstimatedEndTime(), "STRING", adminId);
        saveSetting(KEY_BYPASS, String.valueOf(request.getAllowAdminBypass() != null ? request.getAllowAdminBypass() : true), "BOOLEAN", adminId);
        saveSetting(KEY_UPDATED_BY, adminEmail != null ? adminEmail : "admin", "STRING", adminId);
        
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        saveSetting(KEY_UPDATED_AT, nowStr, "STRING", adminId);

        // Ghi log Audit
        try {
            String actionDesc = (newEnabled ? "BẬT" : "TẮT") + " chế độ bảo trì hệ thống" +
                    (request.getEstimatedEndTime() != null ? " (Dự kiến mở: " + request.getEstimatedEndTime() + ")" : "");
            activityLogService.recordLog(admin, "MAINTENANCE_MODE_TOGGLED", actionDesc, ip, userAgent);
        } catch (Exception e) {
            log.warn("Không thể ghi log bảo trì: {}", e.getMessage());
        }

        log.info("Admin {} đã {} chế độ bảo trì hệ thống: {}", adminEmail, (newEnabled ? "BẬT" : "TẮT"), request.getTitle());
        return getMaintenanceStatus();
    }
}
