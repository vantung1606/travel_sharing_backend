# Hướng dẫn Triển khai Hệ thống Wayfare lên Railway (Cloud & DevOps)

Hệ thống Wayfare Travel Sharing System được đóng gói theo chuẩn **Cloud Native** và **DevOps**, sử dụng **Docker** đa tầng (Multi-stage Build) và tự động hóa qua **GitHub Actions CI/CD**.

---

## 1. Kiến trúc Hạ tầng trên Railway

```
[GitHub Repositories]
         │ (Tự động kích hoạt khi push code)
         ▼
 ┌─────────────────────────────────────────────────────────┐
 │                   RAILWAY PROJECT                       │
 │                                                         │
 │  1. Database: MySQL 8.0 (1-Click Database)              │
 │     └─ Cung cấp URL, Host, Port, User, Password         │
 │                                                         │
 │  2. Backend: wayfare-backend                            │
 │     └─ Chạy Dockerfile (Java 21 / Spring Boot 3.2.4)     │
 │     └─ Tự động liên kết biến môi trường từ MySQL         │
 │     └─ Domain: https://<backend-app>.up.railway.app     │
 │                                                         │
 │  3. Frontend: wayfare-frontend                          │
 │     └─ Chạy Dockerfile (Node 20 -> Nginx Alpine SPA)    │
 │     └─ Biến môi trường: VITE_API_URL trỏ về Backend      │
 │     └─ Domain: https://<frontend-app>.up.railway.app    │
 └─────────────────────────────────────────────────────────┘
```

---

## 2. Quy trình Deploy Chi tiết (3 Bước)

### Bước 1: Khởi tạo Project & MySQL trên Railway
1. Truy cập [railway.com](https://railway.com) và đăng nhập bằng tài khoản **GitHub**.
2. Bấm **New Project** ➔ Chọn **Provision MySQL**.
3. Railway sẽ tạo MySQL 8.0 trong 5 giây. Bấm vào service MySQL ➔ Tab **Variables** để xem các thông tin (`MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, `MYSQLPASSWORD`, `MYSQLDATABASE`).

### Bước 2: Deploy Backend (Spring Boot)
1. Trong cùng project Railway, bấm **+ Create** ➔ Chọn **GitHub Repo** ➔ Chọn `travel_sharing_backend`.
2. Railway sẽ tự động phát hiện `Dockerfile` và build container.
3. Vào tab **Variables** của service Backend, thêm các biến:
   * `SPRING_DATASOURCE_URL` = `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull`
   * `SPRING_DATASOURCE_USERNAME` = `${{MySQL.MYSQLUSER}}`
   * `SPRING_DATASOURCE_PASSWORD` = `${{MySQL.MYSQLPASSWORD}}`
   *(Hoặc Railway hỗ trợ tính năng **Reference Variable** trực tiếp từ MySQL)*.
4. Vào tab **Settings** ➔ Mục **Networking** ➔ Bấm **Generate Domain** để nhận đường link HTTPS công khai (ví dụ: `https://travel-backend-production.up.railway.app`).

### Bước 3: Deploy Frontend (React Vite)
1. Trong cùng project Railway, bấm **+ Create** ➔ Chọn **GitHub Repo** ➔ Chọn `travel_sharing_frontend`.
2. Railway sẽ nhận diện `Dockerfile` và build image Nginx.
3. Vào tab **Variables**, thêm biến:
   * `VITE_API_URL` = `https://<link-backend-o-buoc-2>.up.railway.app/api`
4. Vào tab **Settings** ➔ Mục **Networking** ➔ Bấm **Generate Domain** để nhận link web.

---

## 3. Cách Báo cáo & Show cho Giảng viên

Khi thầy cô kiểm tra đồ án về phần Triển khai (DevOps & Docker):
1. **Show file Dockerfile**:
   * Multi-stage build: Tách biệt môi trường build (JDK / Node) và môi trường chạy (JRE siêu nhẹ / Nginx), giảm kích thước image từ 1GB xuống còn ~150MB.
   * Chạy dưới quyền user phi root (`wayfare`) tăng cường bảo mật.
2. **Show GitHub Actions CI Pipeline**:
   * Mỗi lần đẩy code, GitHub Actions tự động kiểm tra cú pháp, build package và validate Dockerfile.
3. **Show Dashboard Railway**:
   * Minh chứng hệ thống chạy thực tế 24/7 với MySQL, Backend và Frontend hoàn chỉnh.
