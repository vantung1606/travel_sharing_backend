package com.wayfare.config;

import com.wayfare.entity.Notification;
import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import com.wayfare.repository.NotificationRepository;
import com.wayfare.repository.RoleRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationRepository notificationRepository;
    private final com.wayfare.repository.ItineraryRepository itineraryRepository;
    private final com.wayfare.repository.ItineraryDetailRepository itineraryDetailRepository;
    private final com.wayfare.repository.ItineraryMemberRepository itineraryMemberRepository;
    private final com.wayfare.repository.ItineraryExpenseRepository itineraryExpenseRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking sample data initialization...");

        // Ensure Roles exist
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_ADMIN")
                        .description("Quản trị viên hệ thống")
                        .build()));

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("Người dùng tiêu chuẩn")
                        .build()));

        // Create Sample Admin User: admin@gmail.com / admin123
        User adminUser = userRepository.findByEmail("admin@gmail.com")
                .orElseGet(() -> {
                    User user = User.builder()
                            .email("admin@gmail.com")
                            .password(passwordEncoder.encode("admin123"))
                            .fullName("Quản Trị Viên (Admin)")
                            .handle("@admin_wayfare")
                            .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                            .bio("Quản trị viên hệ thống Wayfare Platform 🛡️")
                            .isVerified(true)
                            .roles(Set.of(adminRole, userRole))
                            .build();
                    log.info(">>> SUCCESS: Created Sample Admin Account -> Email: admin@gmail.com | Password: admin123");
                    return userRepository.save(user);
                });

        // Create Sample Member User: linh@gmail.com
        User memberUser = userRepository.findByEmail("linh@gmail.com")
                .orElseGet(() -> {
                    User user = User.builder()
                            .email("linh@gmail.com")
                            .password(passwordEncoder.encode("password123"))
                            .fullName("Linh Hoàng")
                            .handle("@linh_hoang92")
                            .avatarUrl("https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=300&q=80")
                            .bio("Nhiếp ảnh gia du lịch tự túc và mê khám phá văn hóa bản địa")
                            .isVerified(true)
                            .roles(Set.of(userRole))
                            .build();
                    return userRepository.save(user);
                });

        // Seed Sample Notifications if repository is empty
        if (notificationRepository.count() == 0) {
            log.info("Seeding initial notifications into database...");
            List<Notification> sampleNotifications = List.of(
                    Notification.builder()
                            .recipient(adminUser)
                            .actor(memberUser)
                            .type("AI_READY")
                            .message("Trợ lý AI đã tạo xong toàn bộ lịch trình chi tiết 3N2Đ Đà Nẵng - Hội An cho bạn!")
                            .targetUrl("/itineraries")
                            .isRead(false)
                            .build(),
                    Notification.builder()
                            .recipient(adminUser)
                            .actor(memberUser)
                            .type("LIKE")
                            .message("Linh Hoàng đã thích bài viết review 'Săn mây Tà Xùa - Hướng dẫn chi tiết từ A-Z' của bạn.")
                            .targetUrl("/community")
                            .isRead(false)
                            .build(),
                    Notification.builder()
                            .recipient(adminUser)
                            .actor(memberUser)
                            .type("COMMENT")
                            .message("Linh Hoàng đã bình luận: 'Cung đường này đi xe máy có khó khăn vào mùa mưa không bạn?'")
                            .targetUrl("/community")
                            .isRead(false)
                            .build(),
                    Notification.builder()
                            .recipient(adminUser)
                            .actor(memberUser)
                            .type("CHAT_INVITE")
                            .message("Bạn đã được mời tham gia nhóm chuyến đi: 'Phượt Cực Bắc - Mùa Hoa Tam Giác Mạch'.")
                            .targetUrl("/messages")
                            .isRead(true)
                            .build(),
                    Notification.builder()
                            .recipient(adminUser)
                            .actor(null)
                            .type("PLACE_APPROVED")
                            .message("Địa điểm 'Tiệm Cafe Túi Mơ To (Đà Lạt)' do bạn đề xuất đã được duyệt hiển thị trên bản đồ!")
                            .targetUrl("/explore")
                            .isRead(true)
                            .build(),
                    Notification.builder()
                            .recipient(adminUser)
                            .actor(null)
                            .type("SYSTEM")
                            .message("Bản cập nhật WanderAI Core v2.4-turbo: Nâng cấp Semantic Cache giúp tăng 60% tốc độ tạo lịch trình.")
                            .targetUrl("/ai-config")
                            .isRead(true)
                            .build()
            );

            notificationRepository.saveAll(sampleNotifications);
            log.info(">>> SUCCESS: Seeded {} sample notifications for admin account.", sampleNotifications.size());
        }

        // 3. Seed Sample Itineraries if empty
        if (itineraryRepository.count() == 0) {
            log.info("Seeding sample itineraries with explicit physical addresses matching Stitch M10...");

            // Itinerary 1: Da Nang - Hoi An
            com.wayfare.entity.Itinerary daNangTrip = com.wayfare.entity.Itinerary.builder()
                    .creator(adminUser)
                    .title("Hành trình Đà Nẵng - Hội An 4N3Đ: Biển Xanh & Phố Cổ")
                    .destination("Đà Nẵng")
                    .startDate(java.time.LocalDate.now().plusDays(4))
                    .endDate(java.time.LocalDate.now().plusDays(8))
                    .budgetTotal(java.math.BigDecimal.valueOf(7500000))
                    .coverImageUrl("https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=1200&q=80")
                    .isAiGenerated(true)
                    .status("ACTIVE")
                    .build();
            daNangTrip = itineraryRepository.save(daNangTrip);

            // Members for Trip 1
            itineraryMemberRepository.save(com.wayfare.entity.ItineraryMember.builder()
                    .itinerary(daNangTrip)
                    .user(adminUser)
                    .role("OWNER")
                    .build());
            itineraryMemberRepository.save(com.wayfare.entity.ItineraryMember.builder()
                    .itinerary(daNangTrip)
                    .user(memberUser)
                    .role("EDITOR")
                    .build());

            // Details for Trip 1 (Day 1)
            itineraryDetailRepository.save(com.wayfare.entity.ItineraryDetail.builder()
                    .itinerary(daNangTrip)
                    .dayNumber(1)
                    .visitOrder(1)
                    .startTime(java.time.LocalTime.of(8, 0))
                    .locationName("Bãi biển Mỹ Khê")
                    .locationAddress("Đường Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà, TP. Đà Nẵng")
                    .category("Nghỉ dưỡng")
                    .estimatedCost(java.math.BigDecimal.valueOf(150000))
                    .aiTip("Khung giờ tắm biển sóng êm và chụp ảnh nắng sớm đẹp nhất trong ngày.")
                    .transitInfo("10 phút (5 km)")
                    .note("Tắm biển và check-in bình minh.")
                    .build());

            itineraryDetailRepository.save(com.wayfare.entity.ItineraryDetail.builder()
                    .itinerary(daNangTrip)
                    .dayNumber(1)
                    .visitOrder(2)
                    .startTime(java.time.LocalTime.of(10, 30))
                    .locationName("Bán đảo Sơn Trà & Chùa Linh Ứng")
                    .locationAddress("Bán đảo Sơn Trà, Phường Thọ Quang, Quận Sơn Trà, TP. Đà Nẵng")
                    .category("Văn hóa")
                    .estimatedCost(java.math.BigDecimal.valueOf(100000))
                    .aiTip("Nên mang trang phục lịch sự và mang theo mũ nón che nắng khi tham quan tượng Phật Bà.")
                    .transitInfo("15 phút (8 km)")
                    .note("Ngắm toàn cảnh biển Đà Nẵng từ trên cao.")
                    .build());

            itineraryDetailRepository.save(com.wayfare.entity.ItineraryDetail.builder()
                    .itinerary(daNangTrip)
                    .dayNumber(1)
                    .visitOrder(3)
                    .startTime(java.time.LocalTime.of(21, 0))
                    .locationName("Cầu Rồng Phun Lửa")
                    .locationAddress("Đường Nguyễn Văn Linh, Phường Phước Ninh, Quận Hải Châu, TP. Đà Nẵng")
                    .category("Khám phá cảnh quan")
                    .estimatedCost(java.math.BigDecimal.valueOf(50000))
                    .aiTip("Đến trước 20:30 để chọn chỗ đứng trên cầu hoặc quán cafe bờ sông Hàn tránh chen lấn.")
                    .transitInfo("8 phút (3.5 km)")
                    .note("Xem biểu diễn phun lửa và phun nước.")
                    .build());

            // Details for Trip 1 (Day 2)
            itineraryDetailRepository.save(com.wayfare.entity.ItineraryDetail.builder()
                    .itinerary(daNangTrip)
                    .dayNumber(2)
                    .visitOrder(1)
                    .startTime(java.time.LocalTime.of(8, 30))
                    .locationName("Sun World Bà Nà Hills & Cầu Vàng")
                    .locationAddress("Thôn An Sơn, Xã Hòa Ninh, Huyện Hòa Vang, TP. Đà Nẵng")
                    .category("Khám phá cảnh quan")
                    .estimatedCost(java.math.BigDecimal.valueOf(950000))
                    .aiTip("Đi cáp treo chuyến sáng sớm để chụp hình Cầu Vàng khi chưa quá đông khách tour.")
                    .transitInfo("40 phút (28 km)")
                    .note("Trải nghiệm khí hậu 4 mùa và check-in Cầu Vàng.")
                    .build());

            itineraryDetailRepository.save(com.wayfare.entity.ItineraryDetail.builder()
                    .itinerary(daNangTrip)
                    .dayNumber(2)
                    .visitOrder(2)
                    .startTime(java.time.LocalTime.of(17, 30))
                    .locationName("Phố cổ Hội An & Thả đèn hoa đăng")
                    .locationAddress("Phường Minh An, TP. Hội An, Tỉnh Quảng Nam")
                    .category("Văn hóa")
                    .estimatedCost(java.math.BigDecimal.valueOf(350000))
                    .aiTip("Thử ngay nước Mót thảo mộc và đi thuyền thả hoa đăng trên sông Hoài lúc hoàng hôn.")
                    .transitInfo("45 phút (30 km)")
                    .note("Dạo phố cổ ngắm đèn lồng và thưởng thức cao lầu.")
                    .build());

            // Expenses for Trip 1
            itineraryExpenseRepository.save(com.wayfare.entity.ItineraryExpense.builder()
                    .itinerary(daNangTrip)
                    .payer(adminUser)
                    .amount(java.math.BigDecimal.valueOf(2800000))
                    .category("Lưu trú")
                    .description("Khách sạn biển Mỹ Khê 4N3Đ")
                    .build());

            itineraryExpenseRepository.save(com.wayfare.entity.ItineraryExpense.builder()
                    .itinerary(daNangTrip)
                    .payer(memberUser)
                    .amount(java.math.BigDecimal.valueOf(1900000))
                    .category("Vé tham quan")
                    .description("Vé cáp treo Bà Nà Hills x 2 người")
                    .build());

            itineraryExpenseRepository.save(com.wayfare.entity.ItineraryExpense.builder()
                    .itinerary(daNangTrip)
                    .payer(adminUser)
                    .amount(java.math.BigDecimal.valueOf(1250000))
                    .category("Ăn uống")
                    .description("Hải sản Bé Mặn + Ẩm thực Hội An")
                    .build());

            // Itinerary 2: Da Lat
            com.wayfare.entity.Itinerary daLatTrip = com.wayfare.entity.Itinerary.builder()
                    .creator(adminUser)
                    .title("Đà Lạt 3N2Đ: Săn Mây Đồi Trà & Check-in Cà Phê Rừng Thông")
                    .destination("Đà Lạt")
                    .startDate(java.time.LocalDate.now().plusDays(15))
                    .endDate(java.time.LocalDate.now().plusDays(18))
                    .budgetTotal(java.math.BigDecimal.valueOf(4800000))
                    .coverImageUrl("https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=80")
                    .isAiGenerated(true)
                    .status("ACTIVE")
                    .build();
            daLatTrip = itineraryRepository.save(daLatTrip);

            itineraryMemberRepository.save(com.wayfare.entity.ItineraryMember.builder()
                    .itinerary(daLatTrip)
                    .user(adminUser)
                    .role("OWNER")
                    .build());

            log.info(">>> SUCCESS: Seeded sample itineraries (Đà Nẵng, Đà Lạt) with full physical addresses & expenses!");
        }
    }
}
