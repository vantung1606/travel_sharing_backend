package com.wayfare.config;

import com.wayfare.entity.Notification;
import com.wayfare.entity.Province;
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
    private final com.wayfare.repository.ReviewRepository reviewRepository;
    private final com.wayfare.repository.UserFollowRepository userFollowRepository;
    private final com.wayfare.repository.ProvinceRepository provinceRepository;
    private final com.wayfare.repository.UserProvinceVisitRepository userProvinceVisitRepository;
    private final PasswordEncoder passwordEncoder;
    private final javax.sql.DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking sample data initialization...");

        // Ensure Roles exist — CHUẨN DUY NHẤT mọi môi trường (local == prod):
        // cả 3 vai trò của hệ thống phải có sẵn trong bảng roles; thiếu là tự seed bù
        // (trước đây ROLE_MODERATOR chỉ có ở local => prod bấm "Cấp Điều phối viên"
        // sẽ tự chèn dòng rác description='Role').
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

        Role moderatorRole = roleRepository.findByName("ROLE_MODERATOR")
                .orElseGet(() -> {
                    Role moderator = roleRepository.save(Role.builder()
                            .name("ROLE_MODERATOR")
                            .description("Kiểm duyệt viên nội dung")
                            .build());
                    log.info(">>> SUCCESS: Seeded missing role ROLE_MODERATOR (id: {})", moderator.getId());
                    return moderator;
                });

        // Ensure Admin User: admin@gmail.com
        String targetAdminPassword = System.getenv("ADMIN_PASSWORD");
        if (targetAdminPassword == null || targetAdminPassword.isBlank()) {
            targetAdminPassword = "admin123";
        }
        final String finalAdminPassword = targetAdminPassword;

        User adminUser = userRepository.findByEmail("admin@gmail.com")
                .map(existingAdmin -> {
                    boolean needSave = false;
                    if (!passwordEncoder.matches(finalAdminPassword, existingAdmin.getPassword())) {
                        existingAdmin.setPassword(passwordEncoder.encode(finalAdminPassword));
                        needSave = true;
                        log.info(">>> SUCCESS: Synchronized Admin password for admin@gmail.com");
                    }
                    if (existingAdmin.getStatus() == null || !"ACTIVE".equals(existingAdmin.getStatus())) {
                        existingAdmin.setStatus("ACTIVE");
                        needSave = true;
                    }
                    if (Boolean.TRUE.equals(existingAdmin.getIsLocked())) {
                        existingAdmin.setIsLocked(false);
                        needSave = true;
                    }
                    if (!Boolean.TRUE.equals(existingAdmin.getIsVerified())) {
                        existingAdmin.setIsVerified(true);
                        needSave = true;
                    }
                    if (existingAdmin.getRoles() == null || !existingAdmin.getRoles().contains(adminRole)) {
                        Set<Role> roles = new java.util.HashSet<>(existingAdmin.getRoles() != null ? existingAdmin.getRoles() : Set.of());
                        roles.add(adminRole);
                        roles.add(userRole);
                        existingAdmin.setRoles(roles);
                        needSave = true;
                    }
                    return needSave ? userRepository.save(existingAdmin) : existingAdmin;
                })
                .orElseGet(() -> {
                    User user = User.builder()
                            .email("admin@gmail.com")
                            .password(passwordEncoder.encode(finalAdminPassword))
                            .fullName("Quản Trị Viên (Admin)")
                            .handle("@admin_wayfare")
                            .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                            .bio("Quản trị viên hệ thống Wayfare Platform 🛡️")
                            .status("ACTIVE")
                            .isLocked(false)
                            .isVerified(true)
                            .tier("diamond")
                            .roles(Set.of(adminRole, userRole))
                            .build();
                    log.info(">>> SUCCESS: Created Sample Admin Account -> Email: admin@gmail.com | Password: {}", finalAdminPassword);
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
        userRepository.findByEmail("minhanh@gmail.com").ifPresentOrElse(
                minhanh -> {
                    boolean needSave = false;
                    if (!passwordEncoder.matches("password123", minhanh.getPassword())) {
                        minhanh.setPassword(passwordEncoder.encode("password123"));
                        needSave = true;
                        log.info(">>> SUCCESS: Restored password for minhanh@gmail.com to password123");
                    }
                    if (minhanh.getStatus() == null || !"ACTIVE".equals(minhanh.getStatus())) {
                        minhanh.setStatus("ACTIVE");
                        needSave = true;
                    }
                    if (Boolean.TRUE.equals(minhanh.getIsLocked())) {
                        minhanh.setIsLocked(false);
                        needSave = true;
                    }
                    if (needSave) {
                        userRepository.save(minhanh);
                    }
                },
                () -> {
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
                            .roles(Set.of(adminRole, moderatorRole, userRole))
                            .build());
                }
        );

        // Seed Manager (Content Moderator): manager@gmail.com / manager123
        userRepository.findByEmail("manager@gmail.com").ifPresentOrElse(
                manager -> {
                    boolean needSave = false;
                    if (!passwordEncoder.matches("manager123", manager.getPassword())) {
                        manager.setPassword(passwordEncoder.encode("manager123"));
                        needSave = true;
                        log.info(">>> SUCCESS: Restored password for manager@gmail.com to manager123");
                    }
                    if (manager.getStatus() == null || !"ACTIVE".equals(manager.getStatus())) {
                        manager.setStatus("ACTIVE");
                        needSave = true;
                    }
                    if (Boolean.TRUE.equals(manager.getIsLocked())) {
                        manager.setIsLocked(false);
                        needSave = true;
                    }
                    if (!Boolean.TRUE.equals(manager.getIsVerified())) {
                        manager.setIsVerified(true);
                        needSave = true;
                    }
                    if (manager.getRoles() == null || !manager.getRoles().contains(moderatorRole)) {
                        Set<Role> roles = new java.util.HashSet<>(manager.getRoles() != null ? manager.getRoles() : Set.of());
                        roles.add(moderatorRole);
                        roles.add(userRole);
                        manager.setRoles(roles);
                        needSave = true;
                    }
                    if (needSave) {
                        userRepository.save(manager);
                    }
                },
                () -> {
                    userRepository.save(User.builder()
                            .email("manager@gmail.com")
                            .password(passwordEncoder.encode("manager123"))
                            .fullName("Kiểm Duyệt Viên (Manager)")
                            .handle("@manager_wayfare")
                            .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                            .bio("Kiểm duyệt viên hệ thống Wayfare Platform 🛡️")
                            .status("ACTIVE")
                            .isLocked(false)
                            .isVerified(true)
                            .tier("bronze")
                            .roles(Set.of(moderatorRole, userRole))
                            .build());
                    log.info(">>> SUCCESS: Seeded Moderator Account -> Email: manager@gmail.com | Password: manager123");
                }
        );

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

        // Auto-seed Official Destinations (558 records) if places count is small (e.g. Railway or empty DB)
        if (placeRepository.count() < 50) {
            log.info("Auto-seeding 558 official destinations from seed_official_destinations.sql...");
            try {
                org.springframework.jdbc.datasource.init.ResourceDatabasePopulator populator =
                        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator();
                populator.addScript(new org.springframework.core.io.ClassPathResource("seed_official_destinations.sql"));
                populator.setContinueOnError(true);
                populator.setSqlScriptEncoding("UTF-8");
                populator.execute(dataSource);
                log.info(">>> SUCCESS: Auto-seeded official destinations dataset into database! Current count: {}", placeRepository.count());
            } catch (Exception e) {
                log.error("Failed to auto-seed seed_official_destinations.sql: {}", e.getMessage(), e);
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // Seed GIÁ VÉ THỰC TẾ (idempotent): 561/565 địa điểm đang ticketPrice = 0
        // khiến bộ lọc giá bị vô nghĩa. Chỉ đụng vé = 0, GIỮ các giá thật đã có
        // (Túi Mơ To 65k, Hội An 120k, Mã Pí Lèng 120k, Bà Nà Hills 950k).
        // Guard: khi số địa điểm có vé > 0 vượt mốc -> KHÔNG chạy lại.
        // ══════════════════════════════════════════════════════════════════
        List<com.wayfare.entity.Place> allPlaces = placeRepository.findAll();
        if (allPlaces.size() > 400) {
            long paidCount = allPlaces.stream()
                    .filter(p -> p.getTicketPrice() != null && p.getTicketPrice().compareTo(java.math.BigDecimal.ZERO) > 0)
                    .count();
            if (paidCount < 60) {
                log.info("Auto-realistic pricing: {} places have no ticket price -> seeding realistic fares...", paidCount);
                List<com.wayfare.entity.Place> pricedPlaces = new java.util.ArrayList<>();
                for (com.wayfare.entity.Place p : allPlaces) {
                    if (p.getTicketPrice() != null && p.getTicketPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        continue; // giữ giá thật
                    }
                    int fare = seededTicketPrice(p.getId(), p.getCategoryName() == null ? "" : p.getCategoryName());
                    p.setTicketPrice(java.math.BigDecimal.valueOf(fare));
                    p.setPriceRange(fare > 0 ? ("Vé vào cổng: " + formatVndShort(fare) + "đ") : "Miễn phí tham quan");
                    pricedPlaces.add(p);
                }
                placeRepository.saveAll(pricedPlaces);
                log.info(">>> SUCCESS: Seeded realistic ticket prices for {} places (paid rate ~ reaching filter variety)", pricedPlaces.size());
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // Seed REVIEW THẬT CHO BẢNG REVIEWS (idempotent): seed cũ chỉ ghi cột
        // averageRating/reviewCount SẢN trên place (VD 4.9/1280) trong khi bảng
        // reviews trống -> web/mobile hiện "(1280 đánh giá)" mà danh sách = 0.
        // Gieo 6-12 review thực tế/địa điểm, rồi RECOMPUTE lại cột rating/count
        // từ bảng reviews (cùng logic ReviewService.recalculatePlaceRating)
        // để mọi con số hiển thị đều trung thực.
        // Guard: reviewRepository.count() đạt mốc -> không chạy lại.
        // ══════════════════════════════════════════════════════════════════
        if (allPlaces.size() > 400 && reviewRepository.count() < 100) {
            List<com.wayfare.entity.User> authors = userRepository.findAll();
            if (!authors.isEmpty()) {
                log.info("Auto-seeding realistic reviews for {} places from {} user accounts...", allPlaces.size(), authors.size());
                List<com.wayfare.entity.Review> newReviews = new java.util.ArrayList<>();
                List<com.wayfare.entity.Place> touchedPlaces = new java.util.ArrayList<>();
                for (com.wayfare.entity.Place p : allPlaces) {
                    // Trung thực: chỉ gieo review cho nơi đã có rating gốc > 0
                    if (p.getAverageRating() == null || p.getAverageRating().signum() <= 0) {
                        continue;
                    }
                    int n = 6 + (int) (Math.abs(p.getId() * 40503L) % 7); // 6..12 bản ổn định theo id
                    double base = p.getAverageRating().doubleValue();
                    for (int k = 0; k < n; k++) {
                        int jitter = new int[]{0, 1, 0, 0, -1, 1, 0}[(int) ((p.getId() + k * 31L) % 7)];
                        int rating = Math.max(1, Math.min(5, (int) Math.round(base + jitter)));
                        com.wayfare.entity.User author = authors.get((int) ((p.getId() * 13L + k * 29L) % authors.size()));
                        newReviews.add(com.wayfare.entity.Review.builder()
                                .place(p)
                                .author(author)
                                .rating(rating)
                                .comment(seedReviewComment(rating, p.getId() + k * 11L))
                                .build());
                    }
                    touchedPlaces.add(p);
                }
                if (!newReviews.isEmpty()) {
                    reviewRepository.saveAll(newReviews);
                    for (com.wayfare.entity.Place p : touchedPlaces) {
                        Double avg = reviewRepository.calculateAverageRating(p.getId());
                        Long cnt = reviewRepository.countByPlaceId(p.getId());
                        if (avg != null && cnt != null && cnt > 0) {
                            p.setAverageRating(java.math.BigDecimal.valueOf(avg).setScale(1, java.math.RoundingMode.HALF_UP));
                            p.setReviewCount(cnt.intValue());
                        } else {
                            p.setAverageRating(java.math.BigDecimal.ZERO);
                            p.setReviewCount(0);
                        }
                    }
                    placeRepository.saveAll(touchedPlaces);
                    log.info(">>> SUCCESS: Seeded {} reviews & recalculated ratings for {} places — số hiển thị giờ trung thực với bảng reviews", newReviews.size(), touchedPlaces.size());
                }
            }
        }

        // Tự động dọn dẹp các bài viết giả lập vi phạm cũ (nếu có trong DB)
        try {
            List<String> fakeReportTitles = List.of(
                    "Bí kíp cắm trại cấm lửa tại Vườn Quốc Gia Cát Tiên",
                    "Dịch vụ làm visa & bán tour giá rẻ không cọc",
                    "Review homestay Sa Pa cực tệ, bị mất đồ"
            );
            for (String ft : fakeReportTitles) {
                postRepository.findAll().stream()
                        .filter(p -> ft.equals(p.getTitle()))
                        .findFirst()
                        .ifPresent(p -> {
                            postReportRepository.deleteByPostId(p.getId());
                            postRepository.delete(p);
                            log.info(">>> Cleaned fake sample report post from DB: '{}' (ID: {})", p.getTitle(), p.getId());
                        });
            }
        } catch (Exception e) {
            log.warn("Failed to purge legacy fake sample reports: {}", e.getMessage());
        }

        // Seed Sample Community Posts (Nội dung lành mạnh) if empty
        if (postRepository.count() == 0) {
            log.info("Seeding community posts for sharing feed...");
            User hoangNam = userRepository.findByEmail("hoangnam@gmail.com").orElse(adminUser);
            User minhAnh = userRepository.findByEmail("minhanh@gmail.com").orElse(adminUser);
            User tuankiet = userRepository.findByEmail("tuankiet@gmail.com").orElse(adminUser);

            // 1. Safe Community Article: Son Tra Da Nang
            postRepository.save(com.wayfare.entity.Post.builder()
                    .author(minhAnh)
                    .title("Top 5 quán cà phê ngắm hoàng hôn đỉnh nhất bán đảo Sơn Trà")
                    .content("Đến Đà Nẵng đừng quên ghé bán đảo Sơn Trà vào tầm 16h30 để đón khoảnh khắc hoàng hôn buông xuống biển Mỹ Khê tuyệt đẹp. Các quán gợi ý: Tiệm Cà Phê Chân Mây, Sơn Trà Marina...")
                    .category("Ẩm thực & Check-in")
                    .locationTag("Đà Nẵng")
                    .images("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80")
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
                    .images("https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80")
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
                    .images("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80")
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

        // 7. Seed 20 test accounts (tung1 - tung20) with tiers & roles
        seedTwentyTestUsers(adminRole, userRole);

        // 8. Seed danh mục tỉnh thành (34 đơn vị hành chính cấp tỉnh theo sáp nhập 2025)
        seedProvinces();
    }

    /**
     * Danh mục tỉnh thành phục vụ dấu chân du lịch + thống kê phủ sóng.
     * Seed idempotent: chỉ chạy khi bảng provinces còn trống.
     * Alias bao gồm tên các tỉnh CŌ (trước sáp nhập) + tên tiếng Anh để nhận diện
     * từ dữ liệu địa điểm/bài viết đang dùng tên tỉnh cũ.
     */
        private void seedProvinces() {
        try {
            if (provinceRepository.count() == 0) {
                List<Province> provinces = List.of(
                        // MIEN BAC (15)
                        prov("VN01", "Hà Nội", "north", "hà nội,ha noi,hanoi,thủ đô"),
                        prov("VN02", "Hải Phòng", "north", "hải phòng,hai phong,haiphong,hải dương,hai duong,cát bà,đồ sơn"),
                        prov("VN03", "Quảng Ninh", "north", "quảng ninh,quang ninh,hạ long,ha long,cô tô,yên tử"),
                        prov("VN04", "Lào Cai", "north", "lào cai,lao cai,yên bái,yen bai,sa pa,sapa,fansipan,mù cang chải,thác bà"),
                        prov("VN05", "Điện Biên", "north", "điện biên,dien biên,mường thanh,a1"),
                        prov("VN06", "Lai Châu", "north", "lai châu,lai chau,ô quy hồ,sin suối hồ"),
                        prov("VN07", "Sơn La", "north", "sơn la,son la,mộc châu,moc chau,tà xùa,ta xua"),
                        prov("VN08", "Tuyên Quang", "north", "tuyên quang,tuyen quang,hà giang,ha giang,mã pí lèng,đồng văn,lũng cú,na hang"),
                        prov("VN09", "Cao Bằng", "north", "cao bằng,cao bang,bản giốc,pac bo"),
                        prov("VN10", "Thái Nguyên", "north", "thái nguyên,thai nguyen,bắc kạn,bac kan,núi cốc,ba bể"),
                        prov("VN11", "Phú Thọ", "north", "phú thọ,phu tho,vĩnh phúc,vinh phuc,hòa bình,hoa binh,tam đảo,xuân sơn"),
                        prov("VN12", "Lạng Sơn", "north", "lạng sơn,lang son,mẫu sơn,mau son"),
                        prov("VN13", "Bắc Ninh", "north", "bắc ninh,bac ninh,bắc giang,bac giang,quan họ,tây yên tử"),
                        prov("VN14", "Hưng Yên", "north", "hưng yên,hung yen,thái bình,thai binh,phố hiến,cồn vành"),
                        prov("VN15", "Ninh Bình", "north", "ninh bình,ninh binh,hà nam,ha nam,nam định,nam dinh,tràng an,tam cốc"),

                        // MIEN TRUNG (8)
                        prov("VN16", "Thanh Hóa", "central", "thanh hóa,thanh hoa,sầm sơn"),
                        prov("VN17", "Nghệ An", "central", "nghệ an,nghe an,vinh,cửa lò"),
                        prov("VN18", "Hà Tĩnh", "central", "hà tĩnh,ha tinh,bãi biển từng"),
                        prov("VN19", "Quảng Trị", "central", "quảng trị,quang tri,quảng bình,quang binh,đồng hới,phong nha"),
                        prov("VN20", "Huế", "central", "huế,hue,thừa thiên,thừa thiên huế"),
                        prov("VN21", "Đà Nẵng", "central", "đà nẵng,da nang,danang,quảng nam,quang nam,hội an,hoi an,bà na,tam kỳ"),
                        prov("VN22", "Quảng Ngãi", "central", "quảng ngãi,quang ngai,kon tum,đăk tô"),
                        prov("VN23", "Khánh Hòa", "central", "khánh hòa,khanh hoa,nha trang,ninh thuận,ninh thuan"),

                        // TAY NGUYEN (3)
                        prov("VN24", "Gia Lai", "highlands", "gia lai,bình định,binh dinh,quy nhơn,pleiku"),
                        prov("VN25", "Đắk Lắk", "highlands", "đắk lắk,dak lak,phú yên,phu yen,tuy hòa"),
                        prov("VN26", "Lâm Đồng", "highlands", "lâm đồng,lam dong,bình thuận,binh thuan,phan thiết,đắk nông,dak nong"),

                        // MIEN NAM (8)
                        prov("VN27", "TP. Hồ Chí Minh", "south", "hồ chí minh,ho chi minh,sài gòn,saigon,hcm,bà rịa,vũng tàu,bình dương,bình phước"),
                        prov("VN28", "Đồng Nai", "south", "đồng nai,dong nai,biên hòa,bien hoa"),
                        prov("VN29", "Tây Ninh", "south", "tây ninh,tay ninh,long an"),
                        prov("VN30", "Đồng Tháp", "south", "đồng tháp,dong thap,tiền giang,tien giang,mỹ tho"),
                        prov("VN31", "An Giang", "south", "an giang,kiên giang,kien giang,phú quốc,phu quoc,long xuyên"),
                        prov("VN32", "Vĩnh Long", "south", "vĩnh long,vinh long,bến tre,ben tre,trà vinh,tra vinh"),
                        prov("VN33", "Cà Mau", "south", "cà mau,ca mau,bạc liêu,bac lieu"),
                        prov("VN34", "Cần Thơ", "south", "cần thơ,can tho,sóc trăng,soc trang,hậu giang,hau giang")
                );

                provinceRepository.saveAll(provinces);
                log.info(">>> SUCCESS: Seeded {} provinces into 'provinces' table!", provinces.size());
            } else {
                log.info("Province catalog already present ({} rows) - skip seeding.", provinceRepository.count());
            }
        } catch (Exception e) {
            log.error("Failed to seed provinces: {}", e.getMessage());
        }
    }

    private Province prov(String code, String name, String region, String aliases) {
        return Province.builder().code(code).name(name).region(region).aliases(aliases).build();
    }

    private void seedTwentyTestUsers(Role adminRole, Role userRole) {        log.info("Checking/seeding 20 test accounts (tung1@gmail.com -> tung20@gmail.com)...");
        try {
            String defaultPassword = passwordEncoder.encode("123456");
            java.util.Map<Integer, User> userMap = new java.util.HashMap<>();

            // 1. Tạo hoặc cập nhật 20 tài khoản
            for (int i = 1; i <= 20; i++) {
                String email = "tung" + i + "@gmail.com";
                final int userIndex = i;
                User user = userRepository.findByEmail(email).orElse(null);

                if (user == null) {
                    User.UserBuilder builder = User.builder()
                            .email(email)
                            .password(defaultPassword)
                            .status("ACTIVE")
                            .isLocked(false)
                            .isVerified(true);

                    // tung1: CHUẨN DUY NHẤT cho mọi môi trường (local == prod) — luôn ROLE_USER.
                    // CUỘC SỬA: trước đây local tự cấp ROLE_ADMIN (928-941 chia nhánh theo môi trường)
                    // khiến local 3 ADMIN vs prod 2 ADMIN; giờ seed phải "y chang" giữa local & cloud.
                    if (userIndex == 1) {
                        builder.fullName("Nguyễn Thanh Tùng")
                               .handle("@tung1_diamond")
                               .avatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80")
                               .bio("Thành viên sáng giá của cộng đồng Wayfare")
                               .tier("diamond")
                               .roles(Set.of(userRole));
                    } else if (userIndex == 2) {
                        builder.fullName("Trần Tùng (Nhà Thám Hiểm Vàng)")
                               .handle("@tung2_gold")
                               .avatarUrl("https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=300&q=80")
                               .bio("Nhà Thám Hiểm Vàng 👑 | Đam mê chinh phục 63 tỉnh thành Việt Nam")
                               .tier("gold")
                               .roles(Set.of(userRole));
                    } else if (userIndex == 3) {
                        builder.fullName("Lê Tùng (Lữ Hành Bạc)")
                               .handle("@tung3_silver")
                               .avatarUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80")
                               .bio("Lữ Hành Bạc 🥈 | Yêu thích cắm trại và cà phê săn mây")
                               .tier("silver")
                               .roles(Set.of(userRole));
                    } else {
                        builder.fullName("Tùng " + userIndex + " (Tân Thủ Khám Phá)")
                               .handle("@tung" + userIndex + "_explorer")
                               .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                               .bio("Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare")
                               .tier("bronze")
                               .roles(Set.of(userRole));
                    }
                    user = userRepository.save(builder.build());
                    log.info("Created user account: {} (tier: {}, pass: 123456)", email, user.getTier());
                } else {
                    // Chuẩn DUY NHẤT mọi môi trường: KHÔNG đụng mật khẩu user có sẵn và
                    // KHÔNG tự cấp lại role (quyền chỉ thay đổi qua Admin Portal — Princípio
                    // "local == prod": seed không đè thay đổi của người quản trị).
                    if (userIndex == 1) {
                        user.setTier("diamond");
                    } else if (userIndex == 2) {
                        user.setTier("gold");
                    } else if (userIndex == 3) {
                        user.setTier("silver");
                    } else {
                        user.setTier("bronze");
                    }
                    user = userRepository.save(user);
                }
                userMap.put(userIndex, user);
            }

            User tung1 = userMap.get(1);
            User tung2 = userMap.get(2);
            User tung3 = userMap.get(3);

            // 2. Tạo quan hệ Follow để tích lũy XP (tung1 có 10 followers, tung2 có 6 followers, tung3 có 2 followers)
            if (tung1 != null) {
                for (int i = 2; i <= 11; i++) {
                    User follower = userMap.get(i);
                    if (follower != null && userFollowRepository.findByFollowerIdAndFollowingId(follower.getId(), tung1.getId()).isEmpty()) {
                        userFollowRepository.save(com.wayfare.entity.UserFollow.builder()
                                .follower(follower)
                                .following(tung1)
                                .build());
                    }
                }
            }

            if (tung2 != null) {
                for (int i = 5; i <= 10; i++) {
                    User follower = userMap.get(i);
                    if (follower != null && userFollowRepository.findByFollowerIdAndFollowingId(follower.getId(), tung2.getId()).isEmpty()) {
                        userFollowRepository.save(com.wayfare.entity.UserFollow.builder()
                                .follower(follower)
                                .following(tung2)
                                .build());
                    }
                }
            }

            if (tung3 != null) {
                for (int i = 11; i <= 12; i++) {
                    User follower = userMap.get(i);
                    if (follower != null && userFollowRepository.findByFollowerIdAndFollowingId(follower.getId(), tung3.getId()).isEmpty()) {
                        userFollowRepository.save(com.wayfare.entity.UserFollow.builder()
                                .follower(follower)
                                .following(tung3)
                                .build());
                    }
                }
            }

            // 3. Tạo lịch trình và bài viết cho tung1 (Admin Kim Cương >= 800 XP)
            if (tung1 != null) {
                long tung1ItinCount = itineraryRepository.findByCreatorOrderByCreatedAtDesc(tung1).size();
                if (tung1ItinCount < 6) {
                    String[][] sampleItinsTung1 = {
                        {"Hành trình Xuyên Việt 15N14Đ: Từ Hà Giang Đến Mũi Cà Mau", "Hà Giang", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80"},
                        {"Kỳ nghỉ thiên đường tại Đảo Ngọc Phú Quốc 4N3Đ", "Phú Quốc", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80"},
                        {"Khám phá Cố đô Huế & Phố cổ Hội An hoài niệm 3N2Đ", "Huế", "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80"},
                        {"Chinh phục nóc nhà Đông Dương Fansipan & Bản Cát Cát Sapa", "Lào Cai", "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80"},
                        {"Hành trình lặn ngắm san hô Côn Đảo huyền thoại 3N2Đ", "Bà Rịa - Vũng Tàu", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"},
                        {"Săn mây Đà Lạt & Khám phá rừng thông Langbiang 3N2Đ", "Lâm Đồng", "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=800&q=80"}
                    };
                    for (String[] it : sampleItinsTung1) {
                        itineraryRepository.save(com.wayfare.entity.Itinerary.builder()
                                .creator(tung1)
                                .title(it[0])
                                .destination(it[1])
                                .startDate(java.time.LocalDate.now().plusDays(5))
                                .endDate(java.time.LocalDate.now().plusDays(9))
                                .budgetTotal(java.math.BigDecimal.valueOf(8000000))
                                .coverImageUrl(it[2])
                                .isAiGenerated(false)
                                .status("ACTIVE")
                                .build());
                    }
                }

                long tung1PostCount = postRepository.findByAuthorOrderByCreatedAtDesc(tung1).size();
                if (tung1PostCount < 5) {
                    String[][] samplePostsTung1 = {
                        {"Toàn tập kinh nghiệm chinh phục đèo Mã Pí Lèng bằng xe máy", "Cung đèo hiểm trở nhưng đẹp mê hồn. Hãy chuẩn bị áo ấm và kiểm tra phanh kỹ trước khi đổ đèo!", "Hà Giang", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80"},
                        {"Top 5 quán cà phê ngắm hoàng hôn đỉnh nhất tại Phú Quốc", "Ngắm ráng chiều đỏ rực bên bờ biển Bãi Trường là trải nghiệm không thể bỏ lỡ khi ghé đảo ngọc.", "Phú Quốc", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80"},
                        {"Ẩm thực cung đình Huế: Những món ăn tiến vua đậm đà bản sắc", "Bún bò Huế o Cương, chè hẻm 20 món và bánh bột lọc mụ Đỏ là những địa chỉ nhất định phải ghé.", "Thừa Thiên Huế", "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80"},
                        {"Check-in đồi chè Ô Long mùa mai anh đào nở rộ tại Sa Pa", "Sắc hồng hoa mai anh đào nổi bật giữa đồi chè xanh mướt tạo nên khung cảnh lãng mạn như tranh vẽ.", "Sa Pa", "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80"},
                        {"Hành trình tâm linh và lặn biển tại quần đảo Côn Đảo", "Viếng nghĩa trang Hàng Dương thiêng liêng và khám phá bãi Đầm Trầu hoang sơ tuyệt đẹp.", "Côn Đảo", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"}
                    };
                    for (String[] post : samplePostsTung1) {
                        postRepository.save(com.wayfare.entity.Post.builder()
                                .author(tung1)
                                .title(post[0])
                                .content(post[1])
                                .locationTag(post[2])
                                .images(post[3])
                                .category("Cẩm nang du lịch")
                                .likeCount(35)
                                .commentCount(12)
                                .status("ACTIVE")
                                .build());
                    }
                }
            }

            // 4. Tạo lịch trình và bài viết cho tung2 (Cấp Vàng: 400 - 799 XP)
            if (tung2 != null) {
                long tung2ItinCount = itineraryRepository.findByCreatorOrderByCreatedAtDesc(tung2).size();
                if (tung2ItinCount < 4) {
                    String[][] sampleItinsTung2 = {
                        {"Tour Tràng An - Tam Cốc - Bái Đính 2N1Đ trọn vẹn cảnh sắc", "Ninh Bình", "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80"},
                        {"Khám phá Kỳ Co - Eo Gió biển xanh cát vàng Quy Nhơn 3N2Đ", "Bình Định", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"},
                        {"Mộc Châu mùa hoa cải trắng & Thác Dải Yếm 2N1Đ", "Sơn La", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80"},
                        {"Du lịch Đảo Lý Sơn - Thiên đường biển đảo miền Trung 3N2Đ", "Quảng Ngãi", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80"}
                    };
                    for (String[] it : sampleItinsTung2) {
                        itineraryRepository.save(com.wayfare.entity.Itinerary.builder()
                                .creator(tung2)
                                .title(it[0])
                                .destination(it[1])
                                .startDate(java.time.LocalDate.now().plusDays(10))
                                .endDate(java.time.LocalDate.now().plusDays(13))
                                .budgetTotal(java.math.BigDecimal.valueOf(4500000))
                                .coverImageUrl(it[2])
                                .isAiGenerated(false)
                                .status("ACTIVE")
                                .build());
                    }
                }

                long tung2PostCount = postRepository.findByAuthorOrderByCreatedAtDesc(tung2).size();
                if (tung2PostCount < 3) {
                    String[][] samplePostsTung2 = {
                        {"Ghé thăm Tuyệt Tình Cốc Ninh Bình: Điểm check-in ma mị", "Hồ nước xanh ngọc bích lọt thỏm giữa dãy núi đá vôi hùng vĩ là nơi chụp ảnh tuyệt đẹp.", "Ninh Bình", "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80"},
                        {"Eo Gió Quy Nhơn: Con đường đi bộ ven biển đẹp nhất Việt Nam", "Đón bình minh tại Eo Gió với những cơn gió biển lồng lộng sẽ làm bạn xua tan mọi mệt mỏi.", "Quy Nhơn", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"},
                        {"Mùa mận chín Mộc Châu: Trải nghiệm hái mận tại thung lũng Nà Ka", "Vừa hái vừa ăn tại vườn, không khí mát mẻ trong lành cực kỳ sảng khoái.", "Sơn La", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80"}
                    };
                    for (String[] post : samplePostsTung2) {
                        postRepository.save(com.wayfare.entity.Post.builder()
                                .author(tung2)
                                .title(post[0])
                                .content(post[1])
                                .locationTag(post[2])
                                .images(post[3])
                                .category("Địa điểm đẹp")
                                .likeCount(20)
                                .commentCount(6)
                                .status("ACTIVE")
                                .build());
                    }
                }
            }

            // 5. Tạo lịch trình và bài viết cho tung3 (Cấp Bạc: 150 - 399 XP)
            if (tung3 != null) {
                long tung3ItinCount = itineraryRepository.findByCreatorOrderByCreatedAtDesc(tung3).size();
                if (tung3ItinCount < 2) {
                    String[][] sampleItinsTung3 = {
                        {"Phượt Vũng Tàu cuối tuần 2N1Đ: Ngắm biển & Ăn hải sản Bãi Sau", "Bà Rịa - Vũng Tàu", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"},
                        {"Hành trình Mũi Né - Phan Thiết: Đồi Cát Bay & Suối Tiên 2N1Đ", "Bình Thuận", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80"}
                    };
                    for (String[] it : sampleItinsTung3) {
                        itineraryRepository.save(com.wayfare.entity.Itinerary.builder()
                                .creator(tung3)
                                .title(it[0])
                                .destination(it[1])
                                .startDate(java.time.LocalDate.now().plusDays(7))
                                .endDate(java.time.LocalDate.now().plusDays(9))
                                .budgetTotal(java.math.BigDecimal.valueOf(2500000))
                                .coverImageUrl(it[2])
                                .isAiGenerated(false)
                                .status("ACTIVE")
                                .build());
                    }
                }

                long tung3PostCount = postRepository.findByAuthorOrderByCreatedAtDesc(tung3).size();
                if (tung3PostCount < 2) {
                    String[][] samplePostsTung3 = {
                        {"Ngắm hoàng hôn Bãi Trước Vũng Tàu và thưởng thức bánh khọt Gốc Vú Sữa", "Chuyến đi ngắn đổi gió xả stress cuối tuần rất thích hợp cho dân công sở TP.HCM.", "Vũng Tàu", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"},
                        {"Trải nghiệm trượt cát và lái xe địa hình tại Đồi Cát Trắng Mũi Né", "Cảm giác lao dốc trên cát lộng gió cực kỳ phấn khích, hãy thử một lần nhé!", "Mũi Né", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80"}
                    };
                    for (String[] post : samplePostsTung3) {
                        postRepository.save(com.wayfare.entity.Post.builder()
                                .author(tung3)
                                .title(post[0])
                                .content(post[1])
                                .locationTag(post[2])
                                .images(post[3])
                                .category("Kinh nghiệm")
                                .likeCount(8)
                                .commentCount(2)
                                .status("ACTIVE")
                                .build());
                    }
                }
            }

            log.info(">>> SUCCESS: Seeded/Synchronized 20 test accounts (tung1 - tung20)!");
            log.info(">>> - tung1@gmail.com: ROLE_USER dùng chung mọi môi trường -> Cấp Kim Cương (Diamond Legend 💎)");
            log.info(">>> - tung2@gmail.com: ROLE_USER -> Cấp Vàng (Gold Voyager 👑)");
            log.info(">>> - tung3@gmail.com: ROLE_USER -> Cấp Bạc (Silver Wanderer 🥈)");
            log.info(">>> - tung4@gmail.com -> tung20@gmail.com: ROLE_USER -> Cấp Đồng (Bronze Explorer 🥉)");
        } catch (Exception e) {
            log.error("Failed to seed 20 test users: {}", e.getMessage(), e);
        }
    }

    // ── Helper: giá vé deterministic theo id (hash Knuth — cùng pattern pool ảnh) ──
    private static int seededTicketPrice(Long id, String category) {
        long roll = Math.abs(id * 2654435761L) % 100; // 0..99 ổn định theo id
        int price;
        switch (category) {
            case "Quán Cafe & Săn Mây":
                price = 25000 + (int) ((roll % 6) * 10000L); // 25k..75k — quán luôn có phí đồ uống
                break;
            case "Di Sản & Phố Cổ":
                price = roll < 70 ? 20000 + (int) ((roll % 7) * 15000L) : 0; // ~70% có vé 20k..110k
                break;
            case "Danh Lam & Thắng Cảnh":
                price = roll < 80 ? 15000 + (int) ((roll % 6) * 15000L) : 0; // ~80% giá 15k..90k
                break;
            case "Biển Đảo & Nghỉ Dưỡng":
                price = roll < 40 ? 0 : 60000 + (int) ((roll % 9) * 85000L); // ~60% tour 60k..740k
                break;
            default: // Núi Rừng & Mây Ngàn — đa số điểm trekking/săn mây miễn phí
                price = roll < 82 ? 0 : 20000 + (int) ((roll % 9) * 15000L); // ~18% vé 20k..140k
                break;
        }
        return price;
    }

    private static String formatVndShort(int v) {
        return String.format("%,d", v).replace(',', '.');
    }

    // ── Helper: nội dung review tự nhiên theo số sao, chọn ổn định theo seed ──
    private static String seedReviewComment(int rating, long seed) {
        String[] five = {
                "Trải nghiệm tuyệt vời, đẹp hơn cả hình — nhất định quay lại lần nữa!",
                "Rất đáng đến, mọi thứ được chăm chút kỹ lưỡng và sạch sẽ.",
                "Chụp ảnh sống ảo rất xịn, lưu ngay vào sổ check-in của mình.",
                "Đúng chất bản địa, khí trời mát mẻ lắm, 5 sao xứng đáng."
        };
        String[] four = {
                "Đẹp nhưng hơi đông khách lúc chiều, nên đi sáng sớm để chụp ổn hơn.",
                "Khá ưng ý trong tầm giá, bãi đỗ xe rộng và đường đi dễ tìm.",
                "View tuyệt, dịch vụ ổn; nếu nhà vệ sinh được cải thiện thì hơn nữa."
        };
        String[] three = {
                "Ổn cho chuyến đi trong ngày, nhưng khu chờ hơi nóng thiếu mái che.",
                "Cảnh vẫn đẹp, tuy nhiên dịch vụ chưa đồng đều giữa các khung giờ."
        };
        String[] two = {
                "Hơi thất vọng so với hình trên mạng, phí vé khá cao so với trải nghiệm.",
                "Đông và rác nhiều lúc cuối tuần, cần cải thiện công tác bảo quản môi trường."
        };
        String[] one = { "Không như mô tả, chắc không quay lại nữa." };
        String[] pool;
        switch (rating) {
            case 5: pool = five; break;
            case 4: pool = four; break;
            case 3: pool = three; break;
            case 2: pool = two; break;
            default: pool = one; break;
        }
        int idx = (int) (Math.abs(seed * 6364136223846793005L + 1442695040888963407L) % pool.length);
        return pool[idx];
    }
}

