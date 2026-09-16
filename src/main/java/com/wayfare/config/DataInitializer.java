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
    }
}
