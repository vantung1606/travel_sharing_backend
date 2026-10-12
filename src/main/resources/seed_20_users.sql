-- =====================================================================================
-- WAYFARE PLATFORM - SEED SCRIPT FOR 20 TEST USERS & PRESTIGE TIERS
-- Chạy trực tiếp trên Cloud (Railway MySQL Query / phpMyAdmin / DBeaver / MySQL CLI)
-- Tất cả tài khoản có mật khẩu chung: 123456
-- Hash BCrypt cho '123456': $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi
-- =====================================================================================

-- 1. Đảm bảo 2 Role cơ bản tồn tại
INSERT IGNORE INTO roles (id, name, description) VALUES 
(1, 'ROLE_ADMIN', 'Quản trị viên hệ thống'),
(2, 'ROLE_USER', 'Người dùng tiêu chuẩn');

-- 2. Thêm 20 người dùng (tung1@gmail.com -> tung20@gmail.com)
-- Mật khẩu: 123456 (BCrypt Hash: $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi)
INSERT INTO users (email, password, full_name, handle, avatar_url, bio, tier, is_verified, is_locked, status, created_at, updated_at)
VALUES 
-- tung1: ADMIN + CẤP KIM CƯƠNG 💎 (Level 4 - Diamond Legend >= 800 XP)
('tung1@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Nguyễn Thanh Tùng (Admin Kim Cương)', '@tung1_diamond', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80', 'Quản trị viên tối cao hệ thống Wayfare 🛡️ | Huyền Thoại Kim Cương 💎', 'diamond', 1, 0, 'ACTIVE', NOW(), NOW()),

-- tung2: USER + CẤP VÀNG 👑 (Level 3 - Gold Voyager 400 - 799 XP)
('tung2@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Trần Tùng (Nhà Thám Hiểm Vàng)', '@tung2_gold', 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=300&q=80', 'Nhà Thám Hiểm Vàng 👑 | Đam mê chinh phục 63 tỉnh thành Việt Nam', 'gold', 1, 0, 'ACTIVE', NOW(), NOW()),

-- tung3: USER + CẤP BẠC 🥈 (Level 2 - Silver Wanderer 150 - 399 XP)
('tung3@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Lê Tùng (Lữ Hành Bạc)', '@tung3_silver', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80', 'Lữ Hành Bạc 🥈 | Yêu thích cắm trại và cà phê săn mây', 'silver', 1, 0, 'ACTIVE', NOW(), NOW()),

-- tung4 đến tung20: USER + CẤP ĐỒNG 🥉 (Level 1 - Bronze Explorer 0 - 149 XP)
('tung4@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 4 (Tân Thủ Khám Phá)', '@tung4_explorer', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung5@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 5 (Tân Thủ Khám Phá)', '@tung5_explorer', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung6@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 6 (Tân Thủ Khám Phá)', '@tung6_explorer', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung7@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 7 (Tân Thủ Khám Phá)', '@tung7_explorer', 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung8@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 8 (Tân Thủ Khám Phá)', '@tung8_explorer', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung9@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 9 (Tân Thủ Khám Phá)', '@tung9_explorer', 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung10@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 10 (Tân Thủ Khám Phá)', '@tung10_explorer', 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung11@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 11 (Tân Thủ Khám Phá)', '@tung11_explorer', 'https://images.unsplash.com/photo-1488426862026-3ee34a7d66df?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung12@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 12 (Tân Thủ Khám Phá)', '@tung12_explorer', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung13@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 13 (Tân Thủ Khám Phá)', '@tung13_explorer', 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung14@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 14 (Tân Thủ Khám Phá)', '@tung14_explorer', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung15@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 15 (Tân Thủ Khám Phá)', '@tung15_explorer', 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung16@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 16 (Tân Thủ Khám Phá)', '@tung16_explorer', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung17@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 17 (Tân Thủ Khám Phá)', '@tung17_explorer', 'https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung18@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 18 (Tân Thủ Khám Phá)', '@tung18_explorer', 'https://images.unsplash.com/photo-1520813792240-56fc4a3765a7?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung19@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 19 (Tân Thủ Khám Phá)', '@tung19_explorer', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW()),
('tung20@gmail.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'Tùng 20 (Tân Thủ Khám Phá)', '@tung20_explorer', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=300&q=80', 'Tân Thủ Khám Phá 🥉 | Thành viên tích cực của cộng đồng Wayfare', 'bronze', 0, 0, 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    password = VALUES(password),
    tier = VALUES(tier),
    full_name = VALUES(full_name),
    bio = VALUES(bio);

-- 3. Gán Role:
-- tung1@gmail.com có cả ROLE_ADMIN (id=1) và ROLE_USER (id=2)
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.email = 'tung1@gmail.com' AND r.name IN ('ROLE_ADMIN', 'ROLE_USER');

-- tung2 -> tung20 có ROLE_USER (id=2)
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.email LIKE 'tung%@gmail.com' AND u.email != 'tung1@gmail.com' AND r.name = 'ROLE_USER';

-- 4. Tạo quan hệ theo dõi (Follows) để tăng điểm uy tín & xếp hạng
-- tung1 có 10 người theo dõi (tung2 -> tung11 theo dõi tung1)
INSERT IGNORE INTO user_follows (follower_id, following_id, created_at)
SELECT f.id, target.id, NOW()
FROM users f, users target
WHERE target.email = 'tung1@gmail.com' AND f.email IN (
    'tung2@gmail.com', 'tung3@gmail.com', 'tung4@gmail.com', 'tung5@gmail.com', 'tung6@gmail.com',
    'tung7@gmail.com', 'tung8@gmail.com', 'tung9@gmail.com', 'tung10@gmail.com', 'tung11@gmail.com'
);

-- tung2 có 6 người theo dõi (tung5 -> tung10 theo dõi tung2)
INSERT IGNORE INTO user_follows (follower_id, following_id, created_at)
SELECT f.id, target.id, NOW()
FROM users f, users target
WHERE target.email = 'tung2@gmail.com' AND f.email IN (
    'tung5@gmail.com', 'tung6@gmail.com', 'tung7@gmail.com', 'tung8@gmail.com', 'tung9@gmail.com', 'tung10@gmail.com'
);

-- tung3 có 2 người theo dõi (tung11, tung12 theo dõi tung3)
INSERT IGNORE INTO user_follows (follower_id, following_id, created_at)
SELECT f.id, target.id, NOW()
FROM users f, users target
WHERE target.email = 'tung3@gmail.com' AND f.email IN ('tung11@gmail.com', 'tung12@gmail.com');

-- 5. Lịch trình du lịch (Itineraries) cho tung1 (6 lịch trình -> 360 XP)
INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Hành trình Xuyên Việt 15N14Đ: Từ Hà Giang Đến Mũi Cà Mau', 'Hà Giang', CURDATE() + INTERVAL 5 DAY, CURDATE() + INTERVAL 19 DAY, 15000000, 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Kỳ nghỉ thiên đường tại Đảo Ngọc Phú Quốc 4N3Đ', 'Phú Quốc', CURDATE() + INTERVAL 6 DAY, CURDATE() + INTERVAL 10 DAY, 8500000, 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Khám phá Cố đô Huế & Phố cổ Hội An hoài niệm 3N2Đ', 'Huế', CURDATE() + INTERVAL 8 DAY, CURDATE() + INTERVAL 11 DAY, 5500000, 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Chinh phục nóc nhà Đông Dương Fansipan & Bản Cát Cát Sapa', 'Lào Cai', CURDATE() + INTERVAL 12 DAY, CURDATE() + INTERVAL 15 DAY, 6200000, 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Hành trình lặn ngắm san hô Côn Đảo huyền thoại 3N2Đ', 'Bà Rịa - Vũng Tàu', CURDATE() + INTERVAL 16 DAY, CURDATE() + INTERVAL 19 DAY, 7900000, 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Săn mây Đà Lạt & Khám phá rừng thông Langbiang 3N2Đ', 'Lâm Đồng', CURDATE() + INTERVAL 20 DAY, CURDATE() + INTERVAL 23 DAY, 4800000, 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

-- 6. Bài viết cộng đồng cho tung1 (5 bài viết -> 200 XP)
INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Toàn tập kinh nghiệm chinh phục đèo Mã Pí Lèng bằng xe máy', 'Cung đèo hiểm trở nhưng đẹp mê hồn. Hãy chuẩn bị áo ấm và kiểm tra phanh kỹ trước khi đổ đèo!', 'Hà Giang', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80', 'Cẩm nang du lịch', 45, 12, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Top 5 quán cà phê ngắm hoàng hôn đỉnh nhất tại Phú Quốc', 'Ngắm ráng chiều đỏ rực bên bờ biển Bãi Trường là trải nghiệm không thể bỏ lỡ khi ghé đảo ngọc.', 'Phú Quốc', 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80', 'Cẩm nang du lịch', 38, 9, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Ẩm thực cung đình Huế: Những món ăn tiến vua đậm đà bản sắc', 'Bún bò Huế o Cương, chè hẻm 20 món và bánh bột lọc mụ Đỏ là những địa chỉ nhất định phải ghé.', 'Thừa Thiên Huế', 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80', 'Cẩm nang du lịch', 52, 14, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Check-in đồi chè Ô Long mùa mai anh đào nở rộ tại Sa Pa', 'Sắc hồng hoa mai anh đào nổi bật giữa đồi chè xanh mướt tạo nên khung cảnh lãng mạn như tranh vẽ.', 'Sa Pa', 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80', 'Cẩm nang du lịch', 60, 20, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Hành trình tâm linh và lặn biển tại quần đảo Côn Đảo', 'Viếng nghĩa trang Hàng Dương thiêng liêng và khám phá bãi Đầm Trầu hoang sơ tuyệt đẹp.', 'Côn Đảo', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 'Cẩm nang du lịch', 30, 8, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung1@gmail.com';

-- 7. Lịch trình & Bài viết cho tung2 (Cấp Vàng: 4 Lịch trình, 3 Bài viết, 6 Followers -> 550 XP)
INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Tour Tràng An - Tam Cốc - Bái Đính 2N1Đ trọn vẹn cảnh sắc', 'Ninh Bình', CURDATE() + INTERVAL 10 DAY, CURDATE() + INTERVAL 12 DAY, 3500000, 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung2@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Khám phá Kỳ Co - Eo Gió biển xanh cát vàng Quy Nhơn 3N2Đ', 'Bình Định', CURDATE() + INTERVAL 14 DAY, CURDATE() + INTERVAL 17 DAY, 4500000, 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung2@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Mộc Châu mùa hoa cải trắng & Thác Dải Yếm 2N1Đ', 'Sơn La', CURDATE() + INTERVAL 18 DAY, CURDATE() + INTERVAL 20 DAY, 2800000, 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung2@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Du lịch Đảo Lý Sơn - Thiên đường biển đảo miền Trung 3N2Đ', 'Quảng Ngãi', CURDATE() + INTERVAL 22 DAY, CURDATE() + INTERVAL 25 DAY, 4200000, 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung2@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Ghé thăm Tuyệt Tình Cốc Ninh Bình: Điểm check-in ma mị', 'Hồ nước xanh ngọc bích lọt thỏm giữa dãy núi đá vôi hùng vĩ là nơi chụp ảnh tuyệt đẹp.', 'Ninh Bình', 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80', 'Địa điểm đẹp', 25, 5, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung2@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Eo Gió Quy Nhơn: Con đường đi bộ ven biển đẹp nhất Việt Nam', 'Đón bình minh tại Eo Gió với những cơn gió biển lồng lộng sẽ làm bạn xua tan mọi mệt mỏi.', 'Quy Nhơn', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 'Địa điểm đẹp', 30, 8, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung2@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Mùa mận chín Mộc Châu: Trải nghiệm hái mận tại thung lũng Nà Ka', 'Vừa hái vừa ăn tại vườn, không khí mát mẻ trong lành cực kỳ sảng khoái.', 'Sơn La', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80', 'Địa điểm đẹp', 18, 4, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung2@gmail.com';

-- 8. Lịch trình & Bài viết cho tung3 (Cấp Bạc: 2 Lịch trình, 2 Bài viết, 2 Followers -> 280 XP)
INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Phượt Vũng Tàu cuối tuần 2N1Đ: Ngắm biển & Ăn hải sản Bãi Sau', 'Bà Rịa - Vũng Tàu', CURDATE() + INTERVAL 7 DAY, CURDATE() + INTERVAL 9 DAY, 2200000, 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung3@gmail.com';

INSERT INTO itineraries (creator_id, title, destination, start_date, end_date, budget_total, cover_image_url, is_ai_generated, status, created_at, updated_at)
SELECT u.id, 'Hành trình Mũi Né - Phan Thiết: Đồi Cát Bay & Suối Tiên 2N1Đ', 'Bình Thuận', CURDATE() + INTERVAL 12 DAY, CURDATE() + INTERVAL 14 DAY, 2600000, 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80', 0, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung3@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Ngắm hoàng hôn Bãi Trước Vũng Tàu và thưởng thức bánh khọt Gốc Vú Sữa', 'Chuyến đi ngắn đổi gió xả stress cuối tuần rất thích hợp cho dân công sở TP.HCM.', 'Vũng Tàu', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 'Kinh nghiệm', 15, 3, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung3@gmail.com';

INSERT INTO posts (author_id, title, content, location_tag, images, category, like_count, comment_count, status, created_at, updated_at)
SELECT u.id, 'Trải nghiệm trượt cát và lái xe địa hình tại Đồi Cát Trắng Mũi Né', 'Cảm giác lao dốc trên cát lộng gió cực kỳ phấn khích, hãy thử một lần nhé!', 'Mũi Né', 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80', 'Kinh nghiệm', 22, 6, 'ACTIVE', NOW(), NOW()
FROM users u WHERE u.email = 'tung3@gmail.com';
