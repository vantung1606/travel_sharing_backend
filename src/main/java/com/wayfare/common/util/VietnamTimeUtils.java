package com.wayfare.common.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tiện ích chuẩn hóa múi giờ Việt Nam (UTC+7 / Asia/Ho_Chi_Minh) cho toàn bộ hệ thống Wayfare.
 * Tự động đồng bộ giữa dữ liệu lưu theo UTC của Cloud server (Railway) và giờ địa phương Việt Nam.
 * Đảm bảo định dạng 24h và không cần hiển thị giây.
 */
public final class VietnamTimeUtils {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    // Định dạng 24h không có giây: HH:mm dd/MM/yyyy
    public static final DateTimeFormatter FORMATTER_24H_NO_SECONDS = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    // Định dạng chỉ ngày: dd/MM/yyyy
    public static final DateTimeFormatter FORMATTER_DATE_ONLY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private VietnamTimeUtils() {}

    /**
     * Chuyển đổi an toàn một LocalDateTime thành ZonedDateTime theo múi giờ Việt Nam.
     * Tự động nhận diện dữ liệu cũ được lưu theo múi giờ UTC từ máy chủ Cloud để bù 7 tiếng chuẩn xác.
     */
    public static ZonedDateTime toVietnamZonedDateTime(LocalDateTime dt) {
        if (dt == null) return null;
        LocalDateTime nowVn = LocalDateTime.now(VIETNAM_ZONE);

        // Trường hợp A: Quy đổi như thể dt là UTC -> sang giờ Việt Nam
        ZonedDateTime asFromUtc = dt.atZone(UTC_ZONE).withZoneSameInstant(VIETNAM_ZONE);
        // Trường hợp B: dt vốn đã là giờ Việt Nam
        ZonedDateTime asDirectVn = dt.atZone(VIETNAM_ZONE);

        // Nếu quy đổi từ UTC làm thời gian nhảy vào tương lai (> 2 phút), chứng tỏ dt vốn đã là giờ VN
        if (asFromUtc.toLocalDateTime().isAfter(nowVn.plusMinutes(2))) {
            return asDirectVn;
        }

        Duration directDiff = Duration.between(dt, nowVn);
        Duration fromUtcDiff = Duration.between(asFromUtc.toLocalDateTime(), nowVn);

        // Nếu coi là giờ VN mà bị trễ trên 6 tiếng trong quá khứ trong khi quy đổi UTC lại gần hiện tại
        if (directDiff.toHours() >= 6 && !fromUtcDiff.isNegative() && fromUtcDiff.toHours() < directDiff.toHours()) {
            return asFromUtc;
        }

        return asDirectVn;
    }

    /**
     * Chuyển đổi an toàn một LocalDateTime thành LocalDateTime theo giờ Việt Nam
     */
    public static LocalDateTime toVietnamLocalDateTime(LocalDateTime dt) {
        ZonedDateTime zdt = toVietnamZonedDateTime(dt);
        return zdt != null ? zdt.toLocalDateTime() : null;
    }

    /**
     * Tính thời gian tương đối thân thiện theo giờ Việt Nam:
     * - Dưới 1 phút: "Vừa xong"
     * - Dưới 60 phút: "X phút trước"
     * - Dưới 23 giờ: "X giờ trước"
     * - Từ 23 giờ trở lên: "HH:mm dd/MM/yyyy" (chuẩn 24h, không có giây)
     */
    public static String formatTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "Vừa xong";
        ZonedDateTime zdt = toVietnamZonedDateTime(dateTime);
        if (zdt == null) return "Vừa xong";

        ZonedDateTime nowVn = ZonedDateTime.now(VIETNAM_ZONE);
        Duration diff = Duration.between(zdt, nowVn);

        if (diff.isNegative() || diff.getSeconds() < 60) {
            return "Vừa xong";
        }

        long seconds = diff.getSeconds();
        if (seconds < 3600) {
            return (seconds / 60) + " phút trước";
        }

        long hours = seconds / 3600;
        if (hours < 23) {
            return hours + " giờ trước";
        }

        return zdt.format(FORMATTER_24H_NO_SECONDS);
    }

    /**
     * Định dạng ngày giờ 24h không có giây: HH:mm dd/MM/yyyy
     */
    public static String format24hNoSeconds(LocalDateTime dateTime) {
        if (dateTime == null) return "—";
        ZonedDateTime zdt = toVietnamZonedDateTime(dateTime);
        return zdt != null ? zdt.format(FORMATTER_24H_NO_SECONDS) : "—";
    }

    /**
     * Định dạng ngày: dd/MM/yyyy
     */
    public static String formatDateOnly(LocalDateTime dateTime) {
        if (dateTime == null) return "—";
        ZonedDateTime zdt = toVietnamZonedDateTime(dateTime);
        return zdt != null ? zdt.format(FORMATTER_DATE_ONLY) : "—";
    }
}
