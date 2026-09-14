-- Seed Roles
INSERT INTO roles (id, name, description) VALUES 
(1, 'ROLE_USER', 'Người dùng thông thường'),
(2, 'ROLE_ADMIN', 'Quản trị viên hệ thống')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Seed Categories
INSERT INTO categories (id, name, icon, description) VALUES
(1, 'Thắng cảnh thiên nhiên', 'Mountain', 'Vịnh, biển, núi rừng, thác nước'),
(2, 'Ẩm thực & Phố đêm', 'Utensils', 'Quán ăn ngon, đặc sản địa phương'),
(3, 'Di sản văn hóa', 'Landmark', 'Đền chùa, di tích lịch sử, bảo tàng'),
(4, 'Giải trí & Check-in', 'Camera', 'Khu vui chơi, quán cafe view đẹp')
ON DUPLICATE KEY UPDATE name=VALUES(name);
