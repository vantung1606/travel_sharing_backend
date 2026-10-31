package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "permissions",
    uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = {"code"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code; // e.g., PLACES_VIEW, PLACES_APPROVE

    @Column(nullable = false, length = 150)
    private String name; // e.g., "Xem danh sách địa điểm"

    @Column(nullable = false, length = 50)
    private String module; // PLACES, REVIEWS, REPORTS, POSTS, USERS, MODERATORS, THEME, SETTINGS, AUDIT

    @Column(nullable = false, length = 50)
    private String action; // VIEW, APPROVE, HIDE, DELETE, EDIT, LOCK, etc.

    @Column(length = 255)
    private String description;

    @Builder.Default
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = true;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
