package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "provinces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Province {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mã chuẩn VN01..VN34 theo danh mục đơn vị hành chính cấp tỉnh 2025
     * (34 tỉnh thành sau sáp nhập). Unique để serving dấu chân.
     */
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false)
    private String name;

    /** north | central | highlands | south — phục vụ tab vùng miền trên UI. */
    @Column(nullable = false, length = 20)
    private String region;

    /**
     * Danh sách tên gọi/alias: các tỉnh tỉnh cũ trước sáp nhập + cách viết tiếng Anh,
     * phân tách bằng dấu phẩy. Dùng để nhận diện dấu chân từ dữ liệu địa điểm/bài viết.
     */
    @Column(columnDefinition = "TEXT")
    private String aliases;
}
