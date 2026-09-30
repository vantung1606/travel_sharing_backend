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

    // Rule Group 1: Debt Collection, Loan Sharking & Financial Disputes (STRICT BAN)
    private static final List<String> DEBT_COLLECTION_VIOLATIONS = List.of(
            "đòi nợ", "doi no", "trả nợ", "tra no", "quỵt nợ", "quyt no", "bùng nợ", "bung no",
            "vay nợ", "vay no", "vay tiền", "vay tien", "mượn nợ", "muon no", "mượn tiền", "muon tien",
            "cho mượn tiền", "trốn nợ", "tron no", "siết nợ", "siet no", "xiết nợ", "xiet no",
            "nợ xấu", "no xau", "nợ nần", "no nan", "thu nợ", "thu no", "thu hồi nợ", "thu hoi no",
            "con nợ", "con no", "chủ nợ", "chu no", "khất nợ", "khat no", "đáo hạn", "dao han",
            "bán nợ", "ban no", "nhắc nợ", "nhac no", "trả tiền đây", "tra tien day", "mau trả tiền",
            "mau tra tien", "cháy túi vì nợ", "bốc bát họ", "boc bat ho", "tín dụng đen", "tin dung den",
            "lãi ngày", "lai ngay", "lãi suất cao", "cắt tai", "tạt sơn", "ném mắm tôm", "đầu gấu đòi",
            "giang hồ đòi", "nợ tiền", "no tien", "thiếu nợ", "thieu no", "mắc nợ", "mac no"
    );

    // Rule Group 2: Safety, Legal & Eco Conservation Violations
    private static final List<String> ECO_SAFETY_VIOLATIONS = List.of(
            "cấm lửa", "đốt lửa", "lách kiểm lâm", "chốt kiểm lâm", "vượt rào", "chui rào",
            "vào rừng cấm", "săn bắt", "bắt thú", "bẻ san hô", "hái san hô", "vực sâu nguy hiểm",
            "tự ý cắm trại", "cắm trại chui", "vứt rác bừa bãi", "đốt rác"
    );

    // Rule Group 3: Commercial Spam, Scams & Gambling
    private static final List<String> SPAM_SCAM_VIOLATIONS = List.of(
            "cho vay", "vay nóng", "tiền ảo", "crypto", "telegram", "zalo 0", "zalo:", "nhận tiền miễn phí",
            "kiếm tiền online", "hoa hồng cao", "cờ bạc", "đánh bài", "kubet", "bet88", "tài xỉu", "lô đề",
            "đánh đề", "soi cầu"
    );

    // Rule Group 4: Off-topic Commercial, Real Estate, Fake Medicine
    private static final List<String> OFF_TOPIC_COMMERCIAL_VIOLATIONS = List.of(
            "bán đất", "ban dat", "bán nhà", "ban nha", "cho thuê nhà", "bất động sản", "bat dong san",
            "sim số đẹp", "sim so dep", "việc nhẹ lương cao", "chữa bệnh trĩ", "chữa yếu sinh lý",
            "đông y gia truyền", "thuốc nam trị", "thanh lý tủ lạnh", "thanh lý máy giặt"
    );

    // Rule Group 5: Toxicity, Defamation, Slander
    private static final List<String> DEFAMATION_TOXIC_VIOLATIONS = List.of(
            "lừa đảo", "ăn cắp", "trộm cắp", "chó má", "khốn nạn", "súc vật", "đồ lừa đảo",
            "tẩy chay", "bọn lừa tiền", "mất dạy", "vô học"
    );

    // Positive Context: Travel & Tourism Keywords (REQUIRED for auto-approval)
    private static final List<String> TRAVEL_KEYWORDS = List.of(
            "du lịch", "du lich", "phượt", "phuot", "hành trình", "hanh trinh", "chuyến đi", "chuyen di",
            "trải nghiệm", "trai nghiem", "khám phá", "kham pha", "checkin", "check-in", "check in",
            "lịch trình", "lich trinh", "lộ trình", "lo trinh", "tour", "vé máy bay", "ve may bay",
            "chuyến bay", "chuyen bay", "tàu hỏa", "tau hoa", "xe khách", "xe khach", "thuê xe", "thue xe",
            "homestay", "khách sạn", "khach san", "hotel", "resort", "villa", "cắm trại", "cam trai",
            "camping", "glamping", "dựng lều", "dung leu", "trekking", "leo núi", "leo nui", "săn mây", "san may",
            "bình minh", "binh minh", "hoàng hôn", "hoang hon", "ngắm cảnh", "ngam canh", "phong cảnh", "phong canh",
            "bãi biển", "bai bien", "bờ biển", "bo bien", "biển", "bien", "hòn đảo", "hon dao", "đảo", "dao",
            "lặn biển", "lan bien", "suối", "suoi", "thác", "thac", "ngọn đèo", "cung đèo", "đèo", "deo",
            "đỉnh núi", "dinh nui", "hang động", "hang dong", "ẩm thực", "am thuc", "đặc sản", "dac san",
            "món ngon", "mon ngon", "quán ăn", "quan an", "cà phê", "ca phe", "cafe", "sống ảo", "song ao",
            "nghỉ dưỡng", "nghi duong", "kỳ nghỉ", "ky nghi", "dã ngoại", "da ngoai", "tham quan", "điểm đến",
            "diem den", "chụp ảnh", "chup anh", "vali", "ba lô", "balo", "mùa hoa", "mua hoa", "mùa lúa", "mua lua"
    );

    // Popular Tourism Destinations
    private static final List<String> DESTINATION_KEYWORDS = List.of(
            "đà lạt", "da lat", "sa pa", "sapa", "hà giang", "ha giang", "phú quốc", "phu quoc",
            "đà nẵng", "da nang", "hội an", "hoi an", "nha trang", "huế", "hue", "quy nhơn", "quy nhon",
            "vũng tàu", "vung tau", "ninh bình", "ninh binh", "hạ long", "ha long", "mộc châu", "moc chau",
            "tà xùa", "ta xua", "côn đảo", "con dao", "lý sơn", "ly son", "phan thiết", "phan thiet",
            "mũi né", "mui ne", "tây bắc", "tay bac", "miền tây", "mien tay", "cát bà", "cat ba",
            "ba bể", "ba be", "fansipan", "pù luông", "pu luong", "y tý", "y ty", "cô tô", "co to",
            "đồng hới", "dong hoi", "phong nha", "quảng ninh", "quang ninh", "hà nội", "ha noi",
            "sài gòn", "sai gon", "hồ chí minh", "ho chi minh", "mai châu", "mai chau", "tam đảo", "tam dao",
            "cao bằng", "cao bang", "bắc kạn", "bac kan", "lạng sơn", "lang son", "yên bái", "yen bai",
            "điện biên", "dien bien", "lai châu", "lai chau", "an giang", "bến tre", "ben tre",
            "cần thơ", "can tho", "tiền giang", "tien giang", "bangkok", "bali", "singapore", "tokyo", "seoul"
    );

    private String removeAccents(String src) {
        if (src == null) return "";
        String nfd = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }

    public ModerationResult moderate(String title, String content, String locationTag, String category) {
        return moderate(title, content, locationTag, category, false, false);
    }

    public ModerationResult moderate(String title, String content, String locationTag, String category, boolean hasAttachedItinerary) {
        return moderate(title, content, locationTag, category, hasAttachedItinerary, false);
    }

    public ModerationResult moderate(String title, String content, String locationTag, String category, boolean hasAttachedItinerary, boolean isSharedPost) {
        log.info("Running WanderAI Safety Shield evaluation for title: '{}', category: '{}', isSharedPost: {}", title, category, isSharedPost);

        String rawCombined = ((title != null ? title : "") + " " + (content != null ? content : "") + " " + (locationTag != null ? locationTag : "")).toLowerCase();
        String unaccented = removeAccents(rawCombined);

        int score = 100;
        List<String> violations = new ArrayList<>();
        String detectedCategory = "Nội dung an toàn";
        String mainReason = null;

        // 1. STRICT CHECK: DEBT COLLECTION & FINANCIAL SQUEEZE (Bắt buộc chặn đòi nợ)
        for (String kw : DEBT_COLLECTION_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (rawCombined.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 85;
                violations.add("Phát hiện nội dung đòi nợ / tranh chấp tài chính cá nhân: chứa từ khóa '" + kw + "'");
                detectedCategory = "Đòi nợ & Tranh chấp Tài chính Cá nhân";
                mainReason = "Hệ thống phát hiện nội dung mang tính chất đòi nợ, bóc phốt vay mượn hoặc giải quyết mâu thuẫn tiền bạc cá nhân. Wayfare chỉ hỗ trợ nội dung liên quan tới Du lịch & Phượt.";
                break;
            }
        }

        // 2. CHECK: Eco & Fire Safety / Natural Reserve Violations
        for (String kw : ECO_SAFETY_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (rawCombined.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 40;
                violations.add("Vi phạm an toàn du lịch & bảo tồn: chứa cụm từ '" + kw + "'");
                if (mainReason == null) {
                    detectedCategory = "Nguy cơ an toàn & Pháp luật";
                    mainReason = "Thông tin nguy hiểm, tuyên truyền cắm trại cấm lửa hoặc vi phạm quy định bảo tồn rừng đặc dụng/sinh thái.";
                }
            }
        }

        // 3. CHECK: Commercial Spam, Scams & Gambling
        for (String kw : SPAM_SCAM_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (rawCombined.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 50;
                violations.add("Dấu hiệu spam hoặc lừa đảo tài chính: chứa cụm từ '" + kw + "'");
                if (mainReason == null) {
                    detectedCategory = "Spam Thương Mại & Tài Chính";
                    mainReason = "Nội dung quảng cáo dịch vụ trái phép, cờ bạc hoặc kêu gọi giao dịch tài chính không xác thực.";
                }
            }
        }

        // 4. CHECK: Off-topic Commercial / Real Estate / Medicine
        for (String kw : OFF_TOPIC_COMMERCIAL_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (rawCombined.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 45;
                violations.add("Nội dung rao vặt thương mại không thuộc du lịch: chứa cụm từ '" + kw + "'");
                if (mainReason == null) {
                    detectedCategory = "Nội dung Lạc đề & Rao vặt";
                    mainReason = "Nội dung mang tính chất rao vặt thương mại, bất động sản hoặc hàng quán không thuộc lĩnh vực du lịch.";
                }
            }
        }

        // 5. CHECK: Defamation & Toxic Language
        for (String kw : DEFAMATION_TOXIC_VIOLATIONS) {
            String unaccentedKw = removeAccents(kw);
            if (rawCombined.contains(kw) || unaccented.contains(unaccentedKw)) {
                score -= 30;
                violations.add("Nội dung bôi nhọ hoặc công kích gay gắt: chứa cụm từ '" + kw + "'");
                if (mainReason == null) {
                    detectedCategory = "Khiếu nại Đánh giá & Bôi nhọ";
                    mainReason = "Bài viết mang tính cáo buộc đối tác hoặc dùng ngôn từ công kích, cần kiểm tra tính xác thực để tránh gây thiệt hại danh tiếng.";
                }
            }
        }

        // 6. STRICT CHECK: TRAVEL DOMAIN RELEVANCE (Chỉ duyệt bài viết liên quan tới du lịch)
        int travelKeywordHits = 0;
        for (String tkw : TRAVEL_KEYWORDS) {
            String unaccentedTkw = removeAccents(tkw);
            if (rawCombined.contains(tkw) || unaccented.contains(unaccentedTkw)) {
                travelKeywordHits++;
            }
        }

        int destinationHits = 0;
        for (String dest : DESTINATION_KEYWORDS) {
            String unaccentedDest = removeAccents(dest);
            if (rawCombined.contains(dest) || unaccented.contains(unaccentedDest)) {
                destinationHits++;
            }
        }

        boolean hasTravelContext = travelKeywordHits > 0 || destinationHits > 0 || hasAttachedItinerary || isSharedPost;

        // Valid categories that show travel intent (Supports English, slug, and Vietnamese UTF-8 labels)
        boolean hasValidCategory = isSharedPost || (category != null && (
                category.equalsIgnoreCase("KhamPha") ||
                category.equalsIgnoreCase("AmThuc") ||
                category.equalsIgnoreCase("Phuot") ||
                category.equalsIgnoreCase("BienDao") ||
                category.equalsIgnoreCase("NghiDuong") ||
                category.equalsIgnoreCase("CheckIn") ||
                category.toLowerCase().contains("khám phá") ||
                category.toLowerCase().contains("kham pha") ||
                category.toLowerCase().contains("phượt") ||
                category.toLowerCase().contains("phuot") ||
                category.toLowerCase().contains("ẩm thực") ||
                category.toLowerCase().contains("am thuc") ||
                category.toLowerCase().contains("check-in") ||
                category.toLowerCase().contains("checkin") ||
                category.toLowerCase().contains("biển") ||
                category.toLowerCase().contains("bien") ||
                category.toLowerCase().contains("nghỉ dưỡng") ||
                category.toLowerCase().contains("nghi duong") ||
                category.toLowerCase().contains("du lịch") ||
                category.toLowerCase().contains("hành trình") ||
                category.toLowerCase().contains("lịch trình") ||
                category.toLowerCase().contains("tour")
        ));

        if (!hasTravelContext && !hasValidCategory) {
            score -= 45; // If zero travel context and non-travel category, fail auto-approval
            violations.add("Bài viết không chứa các yếu tố, địa danh hay trải nghiệm liên quan đến Du lịch");
            if (mainReason == null) {
                detectedCategory = "Nội dung Lạc đề (Không liên quan Du lịch)";
                mainReason = "Bài viết không chứa thông tin, địa danh hoặc trải nghiệm liên quan đến du lịch, khám phá hay văn hóa ẩm thực. Hệ thống chỉ tự động duyệt các nội dung đúng chủ đề Du lịch.";
            }
        }

        // Clamp score between 10 and 100
        score = Math.max(10, Math.min(100, score));

        boolean approved = score >= 80;
        String badge = approved ? "Nội dung an toàn" : "Chờ Admin duyệt thủ công";

        log.info("WanderAI Moderation Result -> Score: {}/100, Approved: {}, Category: '{}', Violations: {}",
                score, approved, detectedCategory, violations.size());

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
