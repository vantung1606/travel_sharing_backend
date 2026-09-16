# Workspace Instructions & Developer Guidelines

Mỗi khi hỗ trợ người dùng trong workspace **travel_sharing_system**, bạn MUST tuân thủ nghiêm ngặt các nguyên tắc sau:

1. **Quản lý Git & GitHub:**
   - Luôn chú ý làm việc với Git, kiểm tra nhánh (`git status`, `git branch`).
   - Đảm bảo commit và push code an toàn lên nhánh `develop` trên GitHub.

2. **Thiết kế Giao diện (Responsive UI/UX):**
   - Khi chỉnh sửa giao diện Frontend, luôn chú ý kiểm tra hiển thị **Responsive** (Mobile, Tablet, Desktop).
   - Đảm bảo thiết kế hiện đại, mượt mà, không vỡ layout, không tràn màn hình.

3. **Tư duy Backend Cẩn thận & Logic:**
   - Viết code Java Spring Boot chuẩn mực, xử lý cẩn thận các mối quan hệ CSDL và luồng nghiệp vụ.
   - Bắt ngoại lệ và xử lý lỗi chặt chẽ, không nuốt lỗi.

4. **Ghi Log & Thông báo Người dùng:**
   - Khi viết chức năng Backend: Luôn chèn `@Slf4j` log (`log.info`, `log.error`) tại các bước xử lý quan trọng và khi có lỗi.
   - Khi làm Frontend: Luôn có phản hồi/thông báo (Toast, Alert) cho người dùng biết kết quả thao tác.

5. **Chuyên nghiệp & Tỉ mỉ:**
   - Luôn biên dịch/chạy thử lệnh kiểm tra trước khi xác nhận hoàn thành công việc.
