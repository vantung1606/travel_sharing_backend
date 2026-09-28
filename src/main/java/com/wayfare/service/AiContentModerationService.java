package com.wayfare.service;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AiContentModerationService {

    @Getter
    @Builder
    public static class ModerationResult {
        private boolean isApproved;
        private int safetyScore; // 0 to 100
        private String category;
        private String flagReason;
        private String badgeText;
        private List<String> flaggedViolations;
    }

    // Rule Group 1: Safety, Legal & Eco Conservation Violations
    private static final List<String> ECO_SAFETY_VIOLATIONS = List.of(
            "cấm lửa", "đốt lửa", "lách kiểm lâm", "chốt kiểm lâm", "vượt rào", "chui rào",
            "vào rừng cấm", "săn bắt", "bắt thú", "bẻ san hô", "hái san hô", "vực sâu nguy hiểm",
            "tự ý cắm trại", "cắm trại chui", "vứt rác bừa bãi", "đốt rác"
    );

    // Rule Group 2: Commercial Spam, Scams & Gambling
    private static final List<String> SPAM_SCAM_VIOLATIONS = List.of(
            "cho vay", "vay nóng", "tiền ảo", "crypto", "telegram", "zalo 0", "zalo:", "nhận tiền miễn phí",
            "kiếm tiền online", "hoa hồng cao", "cờ bạc", "đánh bài", "kubet", "bet88", "tài xỉu", "lô đề"
    );

    // Rule Group 3: Toxicity, Defamation, Slander
    private static final List<String> DEFAMATION_TOXIC_VIOLATIONS = List.of(
            "lừa đảo", "ăn cắp", "trộm cắp", "chó má", "khốn nạn", "súc vật", "đồ lừa đảo",
            "tẩy chay", "bọn lừa tiền", "mất dạy", "vô học"
    );

    private String removeAccents(String src) {
        if (src == null) return "";
        String nfd = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }

    public ModerationResult moderate(String title, String content, String locationTag, String category) {
        log.info("Running WanderAI Safety Shield evaluation for title: '{}'", title);

        String fullText = ((title != null ? title : "") + " " + (content != null ? content : "") + " " + (locationTag != null ? locationTag : "")).toLowerCase();
        String unaccented = removeAccents(fullText);

        int score = 100;
        List<String> violations = new ArrayList<>();
        String detectedCategory = "Nội dung an toàn";
        String mainReason = null;

        // 1. Check Eco & Fire Safety / Natural Reserve Violations
        for (String kw : ECO_SAFETY_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (fullText.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 40;
                violations.add("Vi phạm an toàn du lịch & bảo tồn: chứa cụm từ '" + kw + "'");
                detectedCategory = "Nguy cơ an toàn & Pháp luật";
                mainReason = "Thông tin nguy hiểm, tuyên truyền cắm trại cấm lửa hoặc vi phạm quy định bảo tồn rừng đặc dụng/sinh thái.";
            }
        }

        // 2. Check Spam & Scams
        for (String kw : SPAM_SCAM_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (fullText.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 45;
                violations.add("Dấu hiệu spam hoặc lừa đảo tài chính: chứa cụm từ '" + kw + "'");
                detectedCategory = "Spam Thương Mại & Tài Chính";
                if (mainReason == null) {
                    mainReason = "Nội dung quảng cáo dịch vụ trái phép, cờ bạc hoặc kêu gọi giao dịch tài chính không xác thực.";
                }
            }
        }

        // 3. Check Defamation & Toxicity
        for (String kw : DEFAMATION_TOXIC_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (fullText.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 30;
                violations.add("Nội dung bôi nhọ hoặc công kích gay gắt: chứa cụm từ '" + kw + "'");
                if (!detectedCategory.contains("an toàn")) {
                    detectedCategory = "Khiếu nại Đánh giá & Bôi nhọ";
                }
                if (mainReason == null) {
                    mainReason = "Bài viết mang tính cáo buộc đối tác hoặc dùng ngôn từ công kích, cần kiểm tra tính xác thực để tránh gây thiệt hại danh tiếng.";
                }
            }
        }

        // Clamp score between 15 and 100
        score = Math.max(15, Math.min(100, score));

        boolean approved = score >= 80;
        String badge = approved ? "Nội dung an toàn" : "Chờ Admin duyệt thủ công";

        log.info("WanderAI Moderation Result -> Score: {}/100, Approved: {}, Violations: {}", score, approved, violations.size());

        return ModerationResult.builder()
                .isApproved(approved)
                .safetyScore(score)
                .category(detectedCategory)
                .flagReason(approved ? null : mainReason)
                .badgeText(badge)
                .flaggedViolations(violations)
                .build();
    }
}
