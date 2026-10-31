package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "itineraries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 150)
    private String destination;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "budget_total", precision = 12, scale = 2)
    private BigDecimal budgetTotal;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Builder.Default
    @Column(name = "is_ai_generated", nullable = false)
    private Boolean isAiGenerated = false;

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Builder.Default
    @Column(length = 30)
    private String status = "ACTIVE"; // DRAFT, ACTIVE, COMPLETED, CANCELLED

    @Column(name = "group_type", length = 50)
    private String groupType; // Cá nhân, Cặp đôi, Bạn bè, Gia đình

    @Column(name = "travel_style", length = 100)
    private String travelStyle; // Nghỉ dưỡng, Phượt / Khám phá, Ẩm thực, Văn hóa & Lịch sử, Sinh thái, Check-in sống ảo

    // === CÁC TIÊU CHÍ TOUR DU LỊCH CHUẨN LỮ HÀNH (15 TIÊU CHÍ) ===
    @Column(name = "tour_code", length = 50)
    private String tourCode; // 1. Mã tour: e.g. "WAY-DN-01"

    @Column(name = "departure_location", length = 150)
    private String departureLocation; // 3. Điểm xuất phát: e.g. "Hà Nội", "TP. Hồ Chí Minh"

    @Column(name = "ending_location", length = 150)
    private String endingLocation; // 3. Điểm kết thúc: e.g. "Đà Nẵng"

    @Column(name = "target_audience", length = 100)
    private String targetAudience; // 4. Đối tượng khách / phân khúc: Thiếu nhi, Thanh niên, Gia đình, Trung niên...

    @Column(name = "max_group_size")
    private Integer maxGroupSize; // 4. Số lượng khách tối đa

    @Column(name = "transportation", length = 150)
    private String transportation; // 5. Phương tiện: Máy bay khứ hồi, Ô tô du lịch đời mới, Tàu hỏa...

    @Column(name = "meal_plan", columnDefinition = "TEXT")
    private String mealPlan; // 7. Ăn uống: Thực đơn dự kiến, bữa sáng buffet, bữa chính đặc sản...

    @Column(name = "accommodation_info", columnDefinition = "TEXT")
    private String accommodationInfo; // 8. Lưu trú: Tên khách sạn, tiêu chuẩn sao (3-5 sao), loại phòng

    @Column(name = "tour_guide_info", columnDefinition = "TEXT")
    private String tourGuideInfo; // 10. Hướng dẫn viên: Thông tin và nhiệm vụ HDV suốt tuyến

    @Column(name = "child_price", precision = 12, scale = 2)
    private BigDecimal childPrice; // 11. Giá tour trẻ em (dưới 11 tuổi)

    @Column(name = "single_room_surcharge", precision = 12, scale = 2)
    private BigDecimal singleRoomSurcharge; // 11. Phụ thu phòng đơn

    @Column(name = "services_included", columnDefinition = "TEXT")
    private String servicesIncluded; // 12. Dịch vụ bao gồm: Vé tham quan, ăn uống, phòng nghỉ, bảo hiểm du lịch...

    @Column(name = "services_excluded", columnDefinition = "TEXT")
    private String servicesExcluded; // 13. Dịch vụ không bao gồm: Chi phí cá nhân, VAT, giặt là, đồ uống...

    @Column(name = "cancellation_policy", columnDefinition = "TEXT")
    private String cancellationPolicy; // 14. Chính sách: Đặt cọc, phương thức thanh toán, hoàn/hủy tour

    @Column(name = "packing_notes", columnDefinition = "TEXT")
    private String packingNotes; // 15. Lưu ý: Hành lý cần mang, trang phục, giấy tờ tùy thân cần thiết

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
