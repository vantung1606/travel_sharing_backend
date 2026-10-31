package com.wayfare.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.entity.ThemeVersion;
import com.wayfare.repository.ThemeVersionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThemeService {

    private final ThemeVersionRepository themeVersionRepository;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final String DEFAULT_THEME_JSON = """
    {
      "colors": {
        "primary": "#B0522F",
        "primaryHover": "#93422A",
        "background": "#F8F4EC",
        "surface": "#FFFFFF",
        "text": "#1E1A15",
        "muted": "#6B6258",
        "border": "#E5DED2",
        "headerBg": "#F8F4EC",
        "sidebarBg": "#1E1A15",
        "sidebarText": "#F8F4EC",
        "buttonBg": "#B0522F",
        "buttonText": "#FFFFFF",
        "success": "#16A34A",
        "warning": "#D97706",
        "danger": "#DC2626",
        "info": "#2563EB"
      },
      "branding": {
        "siteTitle": "Wayfare",
        "tagline": "Hành trình khám phá du lịch Việt Nam theo cách riêng",
        "metaDescription": "Nền tảng chia sẻ lịch trình du lịch, khám phá địa điểm và kết nối cộng đồng phượt thủ hàng đầu Việt Nam.",
        "logoUrl": "/images/brand-logo.png",
        "logoWhiteUrl": "/images/brand-logo-white.png",
        "faviconUrl": "/favicon.ico",
        "heroBgUrl": "",
        "loginBgUrl": "",
        "footerText": "© 2026 Wayfare Travel. Mọi quyền được bảo lưu."
      },
      "components": {
        "radiusCard": "6px",
        "radiusControl": "5px"
      }
    }
    """;

    @PostConstruct
    @Transactional
    public void initDefaultTheme() {
        Optional<ThemeVersion> published = themeVersionRepository.findFirstByStatusOrderByPublishedAtDesc("PUBLISHED");
        if (published.isEmpty()) {
            log.info("Khởi tạo ThemeVersion mặc định ban đầu (v1)...");
            ThemeVersion v1 = ThemeVersion.builder()
                .versionNo(1)
                .configJson(DEFAULT_THEME_JSON.trim())
                .status("PUBLISHED")
                .note("Giao diện chuẩn mặc định Wayfare ban đầu")
                .createdAt(LocalDateTime.now())
                .publishedAt(LocalDateTime.now())
                .build();
            themeVersionRepository.save(v1);
        }
    }

    /**
     * Get currently published active theme
     */
    @Transactional(readOnly = true)
    public ThemeVersion getPublishedTheme() {
        return themeVersionRepository.findFirstByStatusOrderByPublishedAtDesc("PUBLISHED")
            .orElseGet(() -> ThemeVersion.builder()
                .versionNo(1)
                .configJson(DEFAULT_THEME_JSON.trim())
                .status("PUBLISHED")
                .build());
    }

    /**
     * Get current draft theme (or published theme if no draft exists)
     */
    @Transactional(readOnly = true)
    public ThemeVersion getDraftTheme() {
        return themeVersionRepository.findFirstByStatusOrderByCreatedAtDesc("DRAFT")
            .orElseGet(this::getPublishedTheme);
    }

    /**
     * Save draft theme
     */
    @Transactional
    public ThemeVersion saveDraft(String configJson, String note, Long adminId) {
        log.info("Lưu bản nháp theme mới bởi adminId: {}", adminId);
        Optional<ThemeVersion> existingDraftOpt = themeVersionRepository.findFirstByStatusOrderByCreatedAtDesc("DRAFT");
        
        ThemeVersion draft;
        if (existingDraftOpt.isPresent()) {
            draft = existingDraftOpt.get();
            draft.setConfigJson(configJson);
            if (note != null && !note.isBlank()) {
                draft.setNote(note);
            }
        } else {
            int nextVersion = themeVersionRepository.findFirstByOrderByVersionNoDesc()
                .map(t -> t.getVersionNo() + 1)
                .orElse(1);

            draft = ThemeVersion.builder()
                .versionNo(nextVersion)
                .configJson(configJson)
                .status("DRAFT")
                .note(note != null ? note : "Bản nháp giao diện v" + nextVersion)
                .createdBy(adminId)
                .createdAt(LocalDateTime.now())
                .build();
        }
        return themeVersionRepository.save(draft);
    }

    /**
     * Publish current draft or specified version to whole system
     */
    @Transactional
    public ThemeVersion publishCurrentDraft(Long adminId, String ipAddress, String userAgent) {
        ThemeVersion draft = getDraftTheme();
        draft.setStatus("PUBLISHED");
        draft.setPublishedBy(adminId);
        draft.setPublishedAt(LocalDateTime.now());
        ThemeVersion saved = themeVersionRepository.save(draft);

        log.info("Đã xuất bản giao diện phiên bản v{} ra toàn hệ thống", saved.getVersionNo());

        activityLogService.recordLog(
            null,
            "THEME_PUBLISH",
            String.format("Xuất bản giao diện mới phiên bản v%d ra toàn bộ hệ thống.", saved.getVersionNo()),
            ipAddress,
            userAgent
        );
        return saved;
    }

    /**
     * Reset theme to original standard palette
     */
    @Transactional
    public ThemeVersion resetToDefault(Long adminId, String ipAddress, String userAgent) {
        log.info("Khôi phục giao diện về mặc định chuẩn bởi adminId: {}", adminId);
        int nextVersion = themeVersionRepository.findFirstByOrderByVersionNoDesc()
            .map(t -> t.getVersionNo() + 1)
            .orElse(1);

        ThemeVersion resetVersion = ThemeVersion.builder()
            .versionNo(nextVersion)
            .configJson(DEFAULT_THEME_JSON.trim())
            .status("PUBLISHED")
            .note("Khôi phục về bảng màu mặc định chuẩn của Wayfare")
            .createdBy(adminId)
            .publishedBy(adminId)
            .createdAt(LocalDateTime.now())
            .publishedAt(LocalDateTime.now())
            .build();

        ThemeVersion saved = themeVersionRepository.save(resetVersion);

        activityLogService.recordLog(
            null,
            "THEME_RESET",
            String.format("Khôi phục bảng màu và nhận diện giao diện về mặc định gốc (v%d).", saved.getVersionNo()),
            ipAddress,
            userAgent
        );
        return saved;
    }

    /**
     * List all version history
     */
    @Transactional(readOnly = true)
    public List<ThemeVersion> getVersionHistory() {
        return themeVersionRepository.findAllByOrderByVersionNoDesc();
    }

    /**
     * Rollback to a specific past version
     */
    @Transactional
    public ThemeVersion rollbackToVersion(Long versionId, Long adminId, String ipAddress, String userAgent) {
        ThemeVersion target = themeVersionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên bản giao diện ID: " + versionId));

        int nextVersion = themeVersionRepository.findFirstByOrderByVersionNoDesc()
            .map(t -> t.getVersionNo() + 1)
            .orElse(1);

        ThemeVersion rollback = ThemeVersion.builder()
            .versionNo(nextVersion)
            .configJson(target.getConfigJson())
            .status("PUBLISHED")
            .note("Rollback khôi phục từ phiên bản v" + target.getVersionNo())
            .createdBy(adminId)
            .publishedBy(adminId)
            .createdAt(LocalDateTime.now())
            .publishedAt(LocalDateTime.now())
            .build();

        ThemeVersion saved = themeVersionRepository.save(rollback);

        activityLogService.recordLog(
            null,
            "THEME_ROLLBACK",
            String.format("Rollback giao diện về nội dung của phiên bản v%d.", target.getVersionNo()),
            ipAddress,
            userAgent
        );
        return saved;
    }
}
