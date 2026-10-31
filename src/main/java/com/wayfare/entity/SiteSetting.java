package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "site_settings",
    uniqueConstraints = @UniqueConstraint(name = "uk_site_setting_key", columnNames = {"setting_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey; // e.g. branding.site_title, branding.logo_url

    @Column(name = "setting_value", columnDefinition = "LONGTEXT")
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType; // STRING, BOOLEAN, NUMBER, JSON, COLOR, URL, IMAGE

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName; // BRANDING, GENERAL, THEME

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
