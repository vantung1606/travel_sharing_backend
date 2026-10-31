package com.wayfare.modules.itinerary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryDto {
    private Long id;
    private Long creatorId;
    private String creatorName;
    private String creatorEmail;
    private String creatorAvatar;
    private String title;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budgetTotal;
    private String coverImageUrl;
    private Boolean isAiGenerated;
    @Builder.Default
    private Boolean isPublic = true;
    private String status;
    private String groupType;
    private String travelStyle;

    // === CÁC TIÊU CHÍ TOUR DU LỊCH CHUẨN LỮ HÀNH (15 TIÊU CHÍ) ===
    private String tourCode; // 1. Mã tour: e.g. "WAY-DN-01"
    private String departureLocation; // 3. Điểm xuất phát
    private String endingLocation; // 3. Điểm kết thúc
    private String targetAudience; // 4. Phân khúc khách
    private Integer maxGroupSize; // 4. Số lượng khách tối đa
    private String transportation; // 5. Phương tiện chính
    private String mealPlan; // 7. Kế hoạch ăn uống
    private String accommodationInfo; // 8. Khách sạn & lưu trú
    private String tourGuideInfo; // 10. Thông tin Hướng dẫn viên
    private BigDecimal childPrice; // 11. Giá tour trẻ em
    private BigDecimal singleRoomSurcharge; // 11. Phụ thu phòng đơn
    private String servicesIncluded; // 12. Dịch vụ bao gồm
    private String servicesExcluded; // 13. Dịch vụ không bao gồm
    private String cancellationPolicy; // 14. Chính sách hoàn hủy / thanh toán
    private String packingNotes; // 15. Lưu ý hành lý & trang phục

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<ItineraryDetailDto> details = new ArrayList<>();

    @Builder.Default
    private List<ItineraryMemberDto> members = new ArrayList<>();

    @Builder.Default
    private List<ItineraryExpenseDto> expenses = new ArrayList<>();

    private BudgetSummaryDto budgetSummary;
}

