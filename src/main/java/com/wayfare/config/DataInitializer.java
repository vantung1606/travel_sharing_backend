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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
    private final com.wayfare.repository.PostRepository postRepository;
    private final com.wayfare.repository.PostReportRepository postReportRepository;
    private final com.wayfare.repository.PostLikeRepository postLikeRepository;
    private final com.wayfare.repository.PostCommentRepository postCommentRepository;
    private final com.wayfare.repository.PlaceRepository placeRepository;
    private final com.wayfare.repository.UserActivityLogRepository userActivityLogRepository;
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
                            .status("ACTIVE")
                            .isLocked(false)
                            .isVerified(true)
                            .roles(Set.of(userRole))
                            .build();
                    return userRepository.save(user);
                });

        // Seed Minh Anh (Community Moderator)
        if (!userRepository.existsByEmail("minhanh@gmail.com")) {
            userRepository.save(User.builder()
                    .email("minhanh@gmail.com")
                    .password(passwordEncoder.encode("password123"))
                    .fullName("Minh Anh")
                    .handle("@minhanhtravel")
                    .avatarUrl("https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=300&q=80")
                    .bio("Kiểm duyệt viên cộng đồng WanderAI khu vực miền Bắc")
                    .status("ACTIVE")
                    .isLocked(false)
                    .isVerified(true)
                    .roles(Set.of(adminRole, userRole))
                    .build());
        }

        // Seed Hoàng Nam (Gold Member)
        if (!userRepository.existsByEmail("hoangnam@gmail.com")) {
            userRepository.save(User.builder()
                    .email("hoangnam@gmail.com")
                    .password(passwordEncoder.encode("password123"))
                    .fullName("Hoàng Nam")
                    .handle("@namwanderer")
                    .avatarUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80")
                    .bio("Thành viên Vàng - Đã hoàn thành 18 chuyến đi xuyên Việt")
                    .status("ACTIVE")
                    .isLocked(false)
                    .isVerified(true)
                    .roles(Set.of(userRole))
                    .build());
        }

        // Seed Tuấn Kiệt (Local Guide)
        if (!userRepository.existsByEmail("tuankiet@gmail.com")) {
            userRepository.save(User.builder()
                    .email("tuankiet@gmail.com")
                    .password(passwordEncoder.encode("password123"))
                    .fullName("Tuấn Kiệt")
                    .handle("@tuankiet_phuot")
                    .avatarUrl("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=300&q=80")
                    .bio("Hướng dẫn viên địa phương chuyên tour trekking Tà Năng - Phan Dũng")
                    .status("ACTIVE")
                    .isLocked(false)
                    .isVerified(true)
                    .roles(Set.of(userRole))
                    .build());
        }

        // Seed Nguyễn Hoàng Long (Banned / Locked account for audit demonstration)
        if (!userRepository.existsByEmail("long.tourdalat88@gmail.com")) {
            userRepository.save(User.builder()
                    .email("long.tourdalat88@gmail.com")
                    .password(passwordEncoder.encode("password123"))
                    .fullName("Nguyễn Hoàng Long")
                    .handle("@tourgiare_dalat")
                    .avatarUrl("https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=300&q=80")
                    .bio("Tour Đà Lạt giá rẻ chỉ 499k bao ăn ở trọn gói")
                    .status("LOCKED")
                    .isLocked(true)
                    .isVerified(false)
                    .roles(Set.of(userRole))
                    .build());
        }

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

        // Additional Itineraries for Multi-User Realism
        if (itineraryRepository.count() < 4) {
            User hoangNamUser = userRepository.findByEmail("hoangnam@gmail.com").orElse(adminUser);
            User tuankietUser = userRepository.findByEmail("tuankiet@gmail.com").orElse(adminUser);

            com.wayfare.entity.Itinerary haGiangTrip = itineraryRepository.save(com.wayfare.entity.Itinerary.builder()
                    .creator(hoangNamUser)
                    .title("Chinh phục Cực Bắc & Đèo Mã Pí Lèng 4N3Đ")
                    .destination("Hà Giang")
                    .startDate(java.time.LocalDate.now().plusDays(7))
                    .endDate(java.time.LocalDate.now().plusDays(11))
                    .budgetTotal(java.math.BigDecimal.valueOf(3200000))
                    .coverImageUrl("https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=80")
                    .isAiGenerated(true)
                    .status("ACTIVE")
                    .build());

            itineraryMemberRepository.save(com.wayfare.entity.ItineraryMember.builder()
                    .itinerary(haGiangTrip)
                    .user(hoangNamUser)
                    .role("OWNER")
                    .build());

            com.wayfare.entity.Itinerary phuQuocTrip = itineraryRepository.save(com.wayfare.entity.Itinerary.builder()
                    .creator(tuankietUser)
                    .title("Khám phá ẩm thực & biển đêm Hòn Thơm Phú Quốc 3N2Đ")
                    .destination("Phú Quốc")
                    .startDate(java.time.LocalDate.now().plusDays(20))
                    .endDate(java.time.LocalDate.now().plusDays(23))
                    .budgetTotal(java.math.BigDecimal.valueOf(5500000))
                    .coverImageUrl("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=1200&q=80")
                    .isAiGenerated(true)
                    .status("ACTIVE")
                    .build());

            itineraryMemberRepository.save(com.wayfare.entity.ItineraryMember.builder()
                    .itinerary(phuQuocTrip)
                    .user(tuankietUser)
                    .role("OWNER")
                    .build());

            log.info(">>> SUCCESS: Seeded extra itineraries for Hà Giang and Phú Quốc.");
        }

        // Seed Places if repository is empty
        if (placeRepository.count() == 0) {
            log.info("Seeding verified tourist destinations and places...");
            List<com.wayfare.entity.Place> samplePlaces = List.of(
                    com.wayfare.entity.Place.builder()
                            .name("Bãi biển Mỹ Khê")
                            .description("Một trong sáu bãi biển quyến rũ nhất hành tinh được bình chọn bởi tạp chí Forbes với bờ cát trắng mịn và làn nước trong xanh quanh năm.")
                            .address("Đường Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà")
                            .city("Đà Nẵng")
                            .latitude(java.math.BigDecimal.valueOf(16.0617))
                            .longitude(java.math.BigDecimal.valueOf(108.2472))
                            .ticketPrice(java.math.BigDecimal.ZERO)
                            .averageRating(java.math.BigDecimal.valueOf(4.9))
                            .reviewCount(1280)
                            .coverImageUrl("https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=800&q=80")
                            .build(),
                    com.wayfare.entity.Place.builder()
                            .name("Sun World Bà Nà Hills & Cầu Vàng")
                            .description("Quần thể du lịch nghỉ dưỡng kết hợp vui chơi giải trí hàng đầu Việt Nam nổi tiếng với kiệt tác kiến trúc Cầu Vàng trên mây.")
                            .address("Thôn An Sơn, Xã Hòa Ninh, Huyện Hòa Vang")
                            .city("Đà Nẵng")
                            .latitude(java.math.BigDecimal.valueOf(15.9986))
                            .longitude(java.math.BigDecimal.valueOf(107.9961))
                            .ticketPrice(java.math.BigDecimal.valueOf(950000))
                            .averageRating(java.math.BigDecimal.valueOf(4.8))
                            .reviewCount(2450)
                            .coverImageUrl("https://images.unsplash.com/photo-1570789210967-2cac24afeb00?auto=format&fit=crop&w=800&q=80")
                            .build(),
                    com.wayfare.entity.Place.builder()
                            .name("Đèo Mã Pí Lèng & Hẻm Vực Tu Sản")
                            .description("Vua của các con đèo tại Việt Nam uốn lượn trên cao nguyên đá Đồng Văn hùng vĩ với dòng sông Nho Quế xanh như ngọc bích.")
                            .address("Quốc lộ 4C, Xã Pải Lủng, Huyện Mèo Vạc")
                            .city("Hà Giang")
                            .latitude(java.math.BigDecimal.valueOf(23.2428))
                            .longitude(java.math.BigDecimal.valueOf(105.4192))
                            .ticketPrice(java.math.BigDecimal.valueOf(120000))
                            .averageRating(java.math.BigDecimal.valueOf(4.9))
                            .reviewCount(890)
                            .coverImageUrl("https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80")
                            .build(),
                    com.wayfare.entity.Place.builder()
                            .name("Phố cổ Hội An")
                            .description("Di sản văn hóa thế giới được UNESCO công nhận với những dãy nhà cổ màu vàng đặc trưng, đèn lồng rực rỡ và nền ẩm thực phong phú.")
                            .address("Phường Minh An, TP. Hội An")
                            .city("Quảng Nam")
                            .latitude(java.math.BigDecimal.valueOf(15.8801))
                            .longitude(java.math.BigDecimal.valueOf(108.3380))
                            .ticketPrice(java.math.BigDecimal.valueOf(120000))
                            .averageRating(java.math.BigDecimal.valueOf(4.9))
                            .reviewCount(3120)
                            .coverImageUrl("https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80")
                            .build(),
                    com.wayfare.entity.Place.builder()
                            .name("Tiệm Cafe Túi Mơ To")
                            .description("Quán cà phê ngắm hoàng hôn và thung lũng đèn đêm Đà Lạt với khuôn viên hoa cúc họa mi thơ mộng đậm chất vintage.")
                            .address("Hẻm 31 Sào Nam, Phường 11")
                            .city("Đà Lạt")
                            .latitude(java.math.BigDecimal.valueOf(11.9404))
                            .longitude(java.math.BigDecimal.valueOf(108.4583))
                            .ticketPrice(java.math.BigDecimal.valueOf(65000))
                            .averageRating(java.math.BigDecimal.valueOf(4.7))
                            .reviewCount(950)
                            .coverImageUrl("https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=800&q=80")
                            .build(),
                    com.wayfare.entity.Place.builder()
                            .name("Bãi Sao & Quần đảo An Thới")
                            .description("Bãi biển cát trắng mịn như kem tại Phú Quốc với làn nước phẳng lặng và rạn san hô tự nhiên đa sắc màu tuyệt đẹp.")
                            .address("Ấp Bãi Sao, Phường An Thới")
                            .city("Phú Quốc")
                            .latitude(java.math.BigDecimal.valueOf(10.0526))
                            .longitude(java.math.BigDecimal.valueOf(104.0321))
                            .ticketPrice(java.math.BigDecimal.ZERO)
                            .averageRating(java.math.BigDecimal.valueOf(4.8))
                            .reviewCount(1420)
                            .coverImageUrl("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80")
                            .build()
            );

            placeRepository.saveAll(samplePlaces);
            log.info(">>> SUCCESS: Seeded 6 verified tourist places into database!");
        }

        // Seed Sample Community Posts and Reports if empty
        if (postRepository.count() == 0) {
            log.info("Seeding community posts and reports for content moderation...");
            User hoangNam = userRepository.findByEmail("hoangnam@gmail.com").orElse(adminUser);
            User minhAnh = userRepository.findByEmail("minhanh@gmail.com").orElse(adminUser);
            User tuankiet = userRepository.findByEmail("tuankiet@gmail.com").orElse(adminUser);
            User longSpam = userRepository.findByEmail("long.tourdalat88@gmail.com").orElse(adminUser);
            User linh = userRepository.findByEmail("linh@gmail.com").orElse(memberUser);

            // 1. Critical Report: Camping in Cat Tien
            com.wayfare.entity.Post p1 = postRepository.save(com.wayfare.entity.Post.builder()
                    .author(hoangNam)
                    .title("Bí kíp cắm trại cấm lửa tại Vườn Quốc Gia Cát Tiên")
                    .content("Tối qua nhóm mình lách chốt kiểm lâm vào khu sâu suối Đắc Bông, tìm chỗ cỏ lau hạ lều nhóm lửa cực chill, không ai phát hiện được nhé cả nhà. Nhớ mang theo củi khô chút và dọn dẹp trước bình minh để tránh kiểm lâm tuần tra.")
                    .category("Nguy cơ an toàn & Pháp luật")
                    .locationTag("Vườn QG Cát Tiên")
                    .imageUrl("https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?auto=format&fit=crop&w=800&q=80")
                    .likeCount(14)
                    .commentCount(8)
                    .reportsCount(5)
                    .status("PENDING_REPORT")
                    .aiSafetyScore(42)
                    .aiFlagReason("Thông tin nguy hiểm, tuyên truyền cắm trại ở khu bảo tồn nghiêm ngặt không được phép, khuyến khích tự ý đốt lửa trại trong rừng mùa khô gây nguy cơ cháy rừng.")
                    .reportReason("Thông tin nguy hiểm, tuyên truyền cắm trại ở khu bảo tồn nghiêm ngặt không được phép, khuyến khích tự ý đốt lửa trại trong rừng mùa khô gây nguy cơ cháy rừng.")
                    .badgeText("5 Lượt báo cáo")
                    .build());

            postReportRepository.save(com.wayfare.entity.PostReport.builder()
                    .post(p1)
                    .reporter(minhAnh)
                    .category("Nguy cơ an toàn & Pháp luật")
                    .reason("Vi phạm quy chế phòng chống cháy rừng và luật bảo vệ rừng đặc dụng.")
                    .status("PENDING")
                    .build());

            // 2. Critical Report: Visa scam
            com.wayfare.entity.Post p2 = postRepository.save(com.wayfare.entity.Post.builder()
                    .author(longSpam)
                    .title("Dịch vụ làm visa & bán tour giá rẻ không cọc")
                    .content("Cam kết bao đậu visa Schengen 100% không chứng minh tài chính, bao gồm vé bay khứ hồi giá 50%. Nhắn tin Zalo 0909xxx để nhận quà tặng ngay hôm nay, nhận slot ưu đãi có hạn chỉ 3 ngày duy nhất.")
                    .category("Spam Thương Mại & Nghi vấn Scam")
                    .locationTag("Bot Spammer")
                    .imageUrl("https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=800&q=80")
                    .likeCount(2)
                    .commentCount(1)
                    .reportsCount(12)
                    .status("PENDING_REPORT")
                    .aiSafetyScore(12)
                    .aiFlagReason("Hệ thống AI Moderation gắn cờ: Phát hiện cấu trúc câu hàng loạt chứa 3 liên kết rút gọn độc hại lạ và 4 số điện thoại Zalo ảo không qua đăng ký đối tác.")
                    .reportReason("Hệ thống AI Moderation gắn cờ: Phát hiện cấu trúc câu hàng loạt chứa 3 liên kết rút gọn độc hại lạ và 4 số điện thoại Zalo ảo không qua đăng ký đối tác.")
                    .badgeText("AI Scam 92%")
                    .build());

            postReportRepository.save(com.wayfare.entity.PostReport.builder()
                    .post(p2)
                    .reporter(hoangNam)
                    .category("Spam Thương Mại & Nghi vấn Scam")
                    .reason("Quảng cáo dịch vụ tài chính lừa đảo, không có giấy phép du lịch lữ hành quốc tế.")
                    .status("PENDING")
                    .build());

            // 3. Dispute Report: Homestay Sa Pa
            com.wayfare.entity.Post p3 = postRepository.save(com.wayfare.entity.Post.builder()
                    .author(linh)
                    .title("Review homestay Sa Pa cực tệ, bị mất đồ")
                    .content("Phòng ốc ẩm mốc, thái độ nhân viên cực kỳ thiếu tôn trọng. Đặc biệt mình để quên tai nghe AirPods tại bàn lễ tân khi trả phòng và nhân viên chối hoàn toàn, không hỗ trợ check camera.")
                    .category("Khiếu nại Đánh giá & Bôi nhọ")
                    .locationTag("Sa Pa, Lào Cai")
                    .imageUrl("https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80")
                    .likeCount(38)
                    .commentCount(19)
                    .reportsCount(3)
                    .status("PENDING_REPORT")
                    .aiSafetyScore(78)
                    .aiFlagReason("Khiếu nại từ Chủ homestay 'Mây Valley Sa Pa': Khách không có hóa đơn cư trú trùng ngày trên bài đăng. Yêu cầu kiểm tra tính xác thực để tránh gây thiệt hại danh tiếng.")
                    .reportReason("Khiếu nại từ Chủ homestay 'Mây Valley Sa Pa': Khách không có hóa đơn cư trú trùng ngày trên bài đăng. Yêu cầu kiểm tra tính xác thực để tránh gây thiệt hại danh tiếng.")
                    .badgeText("Tranh chấp Đối tác")
                    .build());

            // 4. Safe Community Article: Son Tra Da Nang
            postRepository.save(com.wayfare.entity.Post.builder()
                    .author(minhAnh)
                    .title("Top 5 quán cà phê ngắm hoàng hôn đỉnh nhất bán đảo Sơn Trà")
                    .content("Đến Đà Nẵng đừng quên ghé bán đảo Sơn Trà vào tầm 16h30 để đón khoảnh khắc hoàng hôn buông xuống biển Mỹ Khê tuyệt đẹp. Các quán gợi ý: Tiệm Cà Phê Chân Mây, Sơn Trà Marina...")
                    .category("Ẩm thực & Check-in")
                    .locationTag("Đà Nẵng")
                    .imageUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80")
                    .likeCount(45)
                    .commentCount(12)
                    .reportsCount(0)
                    .status("ACTIVE")
                    .aiSafetyScore(100)
                    .badgeText("Nội dung an toàn")
                    .build());

            // 5. Safe Community Article: Ha Giang phượt
            postRepository.save(com.wayfare.entity.Post.builder()
                    .author(hoangNam)
                    .title("Kinh nghiệm phượt Hà Giang mùa hoa tam giác mạch 3N2Đ")
                    .content("Cung đường đèo Mã Pí Lèng mùa này đẹp nghẹt thở với những cánh đồng hoa tam giác mạch trải dài từ Quản Bạ đến Đồng Văn. Lưu ý chuẩn bị xe máy côn tay bảo dưỡng xích cẩn thận.")
                    .category("Phượt & Khám phá")
                    .locationTag("Hà Giang")
                    .imageUrl("https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80")
                    .likeCount(128)
                    .commentCount(34)
                    .reportsCount(0)
                    .status("ACTIVE")
                    .aiSafetyScore(98)
                    .badgeText("Nội dung an toàn")
                    .build());

            // 6. Safe Community Article: Phu Quoc
            postRepository.save(com.wayfare.entity.Post.builder()
                    .author(tuankiet)
                    .title("Lịch trình khám phá Phú Quốc tự túc tiết kiệm cho nhóm bạn")
                    .content("Chia sẻ kinh nghiệm thuê tàu câu mực đêm tại Hòn Móng Tay và lặn ngắm san hô tự nhiên tại quần đảo An Thới. Chi phí chỉ khoảng 1.200.000đ/người cho cả ngày trải nghiệm biển.")
                    .category("Biển đảo & Nghỉ dưỡng")
                    .locationTag("Phú Quốc")
                    .imageUrl("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80")
                    .likeCount(89)
                    .commentCount(22)
                    .reportsCount(0)
                    .status("ACTIVE")
                    .aiSafetyScore(100)
                    .badgeText("Nội dung an toàn")
                    .build());

            log.info(">>> SUCCESS: Seeded 6 sample community posts with AI Safety metrics and escalation reports!");
        }

        // Link community posts with itineraries and seed realistic comments
        try {
            List<com.wayfare.entity.Post> allPosts = postRepository.findAll();
            com.wayfare.entity.Itinerary daNangItin = itineraryRepository.findAll().stream()
                    .filter(i -> i.getDestination() != null && i.getDestination().contains("Đà Nẵng"))
                    .findFirst().orElse(null);
            com.wayfare.entity.Itinerary haGiangItin = itineraryRepository.findAll().stream()
                    .filter(i -> i.getDestination() != null && i.getDestination().contains("Hà Giang"))
                    .findFirst().orElse(null);
            com.wayfare.entity.Itinerary phuQuocItin = itineraryRepository.findAll().stream()
                    .filter(i -> i.getDestination() != null && i.getDestination().contains("Phú Quốc"))
                    .findFirst().orElse(null);

            for (com.wayfare.entity.Post p : allPosts) {
                if (p.getItinerary() == null && "ACTIVE".equals(p.getStatus())) {
                    if (p.getLocationTag() != null && p.getLocationTag().contains("Đà Nẵng") && daNangItin != null) {
                        p.setItinerary(daNangItin);
                        postRepository.save(p);
                        log.info("Linked post '{}' with itinerary '{}'", p.getTitle(), daNangItin.getTitle());
                    } else if (p.getLocationTag() != null && p.getLocationTag().contains("Hà Giang") && haGiangItin != null) {
                        p.setItinerary(haGiangItin);
                        postRepository.save(p);
                        log.info("Linked post '{}' with itinerary '{}'", p.getTitle(), haGiangItin.getTitle());
                    } else if (p.getLocationTag() != null && p.getLocationTag().contains("Phú Quốc") && phuQuocItin != null) {
                        p.setItinerary(phuQuocItin);
                        postRepository.save(p);
                        log.info("Linked post '{}' with itinerary '{}'", p.getTitle(), phuQuocItin.getTitle());
                    }
                }
            }

            if (postCommentRepository.count() == 0) {
                User linhUser = userRepository.findByEmail("linh@gmail.com").orElse(null);
                User adminUserRef = userRepository.findByEmail("admin@gmail.com").orElse(null);
                User hoangNamUser = userRepository.findByEmail("hoangnam@gmail.com").orElse(null);

                for (com.wayfare.entity.Post p : allPosts) {
                    if ("ACTIVE".equals(p.getStatus()) && p.getItinerary() != null) {
                        postCommentRepository.save(com.wayfare.entity.PostComment.builder()
                                .post(p)
                                .author(linhUser != null ? linhUser : adminUserRef)
                                .content("Lịch trình đính kèm này chi tiết và hợp lý quá! Mình vừa bấm Sao chép tour vào kho cá nhân rồi, cảm ơn tác giả nhiều nhé!")
                                .createdAt(LocalDateTime.now().minusHours(3))
                                .build());

                        postCommentRepository.save(com.wayfare.entity.PostComment.builder()
                                .post(p)
                                .author(hoangNamUser != null ? hoangNamUser : adminUserRef)
                                .content("Quá đỉnh! Điểm đến toàn chỗ đẹp chuẩn phong cách du lịch trải nghiệm.")
                                .createdAt(LocalDateTime.now().minusHours(1))
                                .build());

                        // Update comment count
                        p.setCommentCount(2);
                        postRepository.save(p);
                    }
                }
                log.info(">>> SUCCESS: Seeded sample comments for active community posts!");
            }
        } catch (Exception e) {
            log.error("Failed to link itineraries or seed comments: {}", e.getMessage());
        }

        // =====================================================================
        // SEED SYSTEM AUDIT LOGS (USER_ACTIVITY_LOGS)
        // =====================================================================
        if (userActivityLogRepository.count() == 0) {
            log.info("Seeding realistic system audit and activity logs...");

            User admin = userRepository.findByEmail("admin@gmail.com").orElse(null);
            User linh = userRepository.findByEmail("linh@gmail.com").orElse(null);
            User minhanh = userRepository.findByEmail("minhanh@gmail.com").orElse(null);
            User hoangnam = userRepository.findByEmail("hoangnam@gmail.com").orElse(null);
            User tuankiet = userRepository.findByEmail("tuankiet@gmail.com").orElse(null);

            LocalDateTime now = LocalDateTime.now();

            List<com.wayfare.entity.UserActivityLog> logs = List.of(
                    com.wayfare.entity.UserActivityLog.builder()
                            .user(admin)
                            .action("LOGIN")
                            .details("Quản trị viên đăng nhập vào hệ thống Wayfare Portal")
                            .ipAddress("127.0.0.1")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .createdAt(now.minusMinutes(12))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(admin)
                            .action("USER_MANAGEMENT")
                            .details("Cập nhật trạng thái người dùng: spammer@gmail.com (Trạng thái: LOCKED, Lý do: Báo cáo vi phạm nhiều lần)")
                            .ipAddress("127.0.0.1")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .createdAt(now.minusMinutes(42))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(minhanh)
                            .action("RESOLVE_REPORT")
                            .details("Xử lý báo cáo #102: Khóa tạm thời bài viết vi phạm quảng cáo trái phép của tài khoản @spammer_vn")
                            .ipAddress("113.161.45.22")
                            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15")
                            .createdAt(now.minusHours(1).minusMinutes(25))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(linh)
                            .action("CREATE_ITINERARY")
                            .details("Tạo mới lịch trình du lịch: 'Khám phá Đà Nẵng - Hội An (3N2Đ)' với ngân sách dự kiến 4.500.000₫")
                            .ipAddress("171.244.12.89")
                            .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")
                            .createdAt(now.minusHours(2).minusMinutes(50))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(hoangnam)
                            .action("AI_PLANNER_GENERATE")
                            .details("Yêu cầu AI lập lộ trình tự động: Điểm đến Sapa, thời gian 3 ngày 2 đêm, phong cách Phiêu lưu & Khám phá")
                            .ipAddress("14.162.180.50")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0")
                            .createdAt(now.minusHours(5).minusMinutes(15))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(linh)
                            .action("LOGIN")
                            .details("Người dùng đăng nhập thành công qua thiết bị Mobile Safari")
                            .ipAddress("171.244.12.89")
                            .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")
                            .createdAt(now.minusHours(8).minusMinutes(30))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(tuankiet)
                            .action("CREATE_POST")
                            .details("Đăng bài viết mới lên Cộng đồng: 'Lịch trình khám phá Phú Quốc tự túc tiết kiệm cho nhóm bạn'")
                            .ipAddress("118.69.190.10")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .createdAt(now.minusHours(14).minusMinutes(10))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(admin)
                            .action("UPDATE_PLACE")
                            .details("Cập nhật thông tin điểm đến: 'Bà Nà Hills & Cầu Vàng' (Bổ sung giá vé cáp treo mới nhất)")
                            .ipAddress("127.0.0.1")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .createdAt(now.minusDays(1).minusHours(2).minusMinutes(15))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(admin)
                            .action("ROLE_MANAGEMENT")
                            .details("Gán vai trò Quản trị viên/Kiểm duyệt (ROLE_ADMIN) cho người dùng Minh Anh (@minhanhtravel)")
                            .ipAddress("127.0.0.1")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .createdAt(now.minusDays(1).minusHours(6).minusMinutes(40))
                            .build(),

                    com.wayfare.entity.UserActivityLog.builder()
                            .user(tuankiet)
                            .action("REGISTER")
                            .details("Đăng ký tài khoản người dùng mới thành công: tuankiet@gmail.com")
                            .ipAddress("118.69.190.10")
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                            .createdAt(now.minusDays(2).minusHours(3).minusMinutes(20))
                            .build()
            );

            userActivityLogRepository.saveAll(logs);
            log.info(">>> SUCCESS: Seeded {} realistic system audit logs into user_activity_logs!", logs.size());
        } else {
            // Rebalance existing logs if their timestamps are artificially clustered on the exact same second
            List<com.wayfare.entity.UserActivityLog> existingLogs = userActivityLogRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            boolean clustered = false;
            for (int i = 0; i < existingLogs.size() - 1; i++) {
                LocalDateTime t1 = existingLogs.get(i).getCreatedAt();
                LocalDateTime t2 = existingLogs.get(i + 1).getCreatedAt();
                if (t1 != null && t2 != null &&
                    t1.getHour() == t2.getHour() &&
                    t1.getMinute() == t2.getMinute() &&
                    t1.getSecond() == t2.getSecond()) {
                    clustered = true;
                    break;
                }
            }

            if (clustered) {
                log.info("Found clustered audit log timestamps. Rebalancing them across realistic historical timelines...");
                LocalDateTime now = LocalDateTime.now();
                int total = existingLogs.size();
                for (int i = 0; i < total; i++) {
                    com.wayfare.entity.UserActivityLog logItem = existingLogs.get(i);
                    int offsetFromLatest = total - 1 - i;
                    switch (offsetFromLatest) {
                        case 0: logItem.setCreatedAt(now.minusMinutes(3)); break;
                        case 1: logItem.setCreatedAt(now.minusMinutes(16)); break;
                        case 2: logItem.setCreatedAt(now.minusMinutes(35)); break;
                        case 3: logItem.setCreatedAt(now.minusHours(1).minusMinutes(12)); break;
                        case 4: logItem.setCreatedAt(now.minusHours(2).minusMinutes(40)); break;
                        case 5: logItem.setCreatedAt(now.minusHours(4).minusMinutes(15)); break;
                        case 6: logItem.setCreatedAt(now.minusHours(7).minusMinutes(22)); break;
                        case 7: logItem.setCreatedAt(now.minusHours(11).minusMinutes(45)); break;
                        case 8: logItem.setCreatedAt(now.minusHours(16).minusMinutes(10)); break;
                        case 9: logItem.setCreatedAt(now.minusDays(1).minusHours(1).minusMinutes(30)); break;
                        case 10: logItem.setCreatedAt(now.minusDays(1).minusHours(5).minusMinutes(15)); break;
                        case 11: logItem.setCreatedAt(now.minusDays(1).minusHours(12).minusMinutes(50)); break;
                        case 12: logItem.setCreatedAt(now.minusDays(2).minusHours(2).minusMinutes(20)); break;
                        default: logItem.setCreatedAt(now.minusDays(2 + (offsetFromLatest - 12))); break;
                    }
                }
                userActivityLogRepository.saveAll(existingLogs);
                log.info(">>> SUCCESS: Rebalanced {} audit log timestamps across natural days and hours!", existingLogs.size());
            }
        }
    }
}

