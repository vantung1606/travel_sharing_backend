---
name: developer-workflow
description: Quy chuẩn làm việc chuyên nghiệp với GitHub, thiết kế Responsive UI/UX, logic Backend cẩn thận, chèn log hệ thống và thông báo người dùng cho dự án Wayfare.
---

# Quy Chuẩn Phát Triển & Chuẩn Mực Làm Việc (Developer Workflow)

Tài liệu này quy định các nguyên tắc làm việc cốt lõi cần tuân thủ trong suốt quá trình phát triển dự án **Wayfare Travel Sharing System**.

---

## 1. Quản Lý Mã Nguồn & GitHub (Git Workflow)
- **Kiểm tra nhánh:** Luôn kiểm tra nhánh làm việc hiện tại (`git status`, `git branch`) trước khi tiến hành chỉnh sửa mã nguồn.
- **Commit chuẩn mực:** Đặt câu lệnh commit ngắn gọn, rõ ràng theo chuẩn Semantic Commit Messages (`feat:`, `fix:`, `style:`, `chore:`, `refactor:`).
- **Đồng bộ GitHub:** Đảm bảo commit và push code an toàn lên đúng nhánh phát triển (ưu tiên nhánh `develop`) trên GitHub.

---

## 2. Thiết Kế Giao Diện & Responsive UI/UX (Frontend)
- **Đa thiết bị (Responsive):** Luôn kiểm tra và tối ưu giao diện trên tất cả các kích thước màn hình:
  * **Mobile:** `< 640px` (Dùng menu slide-over/drawer, icon gọn gàng).
  * **Tablet:** `640px - 1024px` (Tối ưu bố cục grid/flex).
  * **Desktop:** `> 1024px` (Hiển thị đầy đủ tính năng & thanh điều hướng).
- **Trải nghiệm người dùng (UX):**
  * Không để xảy ra lỗi vỡ khung, tràn viền màn hình (horizontal overflow).
  * Áp dụng phong cách thiết kế hiện đại (Glassmorphism, mượt mà, hover hiệu ứng, tương tác phản hồi tức thì).

---

## 3. Phát Triển Backend & Cơ Sở Dữ Liệu (Backend Logic)
- **Clean Code & Chặt chẽ:** Viết mã nguồn Java 17/21 & Spring Boot 3 gọn gàng, tuân thủ nguyên tắc SOLID.
- **Ràng buộc CSDL:** Quản lý Entity JPA, khóa chính, khóa ngoại (`@ManyToOne`, `@OneToMany`, `@JoinColumn`) và mối quan hệ giữa các bảng chuẩn xác.
- **Xử lý ngoại lệ (Exception Handling):** Bắt lỗi cẩn thận với `@RestControllerAdvice` hoặc `try-catch`, tuyệt đối không nuốt lỗi âm thầm (`silent fail`).

---

## 4. Ghi Log Hệ Thống & Thông Báo (Logging & Notifications)
- **Backend Logging:**
  * Luôn sử dụng `@Slf4j` để ghi nhận các mốc xử lý quan trọng trong Service và Controller.
  * Phân loại log đúng cấp độ:
    * `log.info()`: Khi khởi tạo hành động, thực thi API thành công.
    * `log.warn()`: Cảnh báo truy cập trái phép, dữ liệu nghi vấn.
    * `log.error()`: Khi xảy ra ngoại lệ/lỗi hệ thống (kèm chi tiết exception stacktrace).
- **Frontend Notifications:**
  * Luôn gửi phản hồi trực quan cho người dùng (Toast notification, Alert modal, Status indicator) khi thực hiện các thao tác Đăng nhập, Tạo lịch trình, Đăng bài, Xóa dữ liệu,...

---

## 5. Phong Cách Làm Việc Chuyên Nghiệp
- **Kiểm thử trước khi bàn giao:** Luôn biên dịch (`mvnw compile`, `npm run build`) và chạy thử để xác nhận hệ thống không bị lỗi trước khi báo hoàn thành.
- **Tỉ mỉ & Trách nhiệm:** Làm việc cẩn trọng, tập trung vào giải pháp lâu dài thay vì vá lỗi tạm thời.
