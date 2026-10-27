package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "places")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "ticket_price", precision = 12, scale = 2)
    private BigDecimal ticketPrice;

    @Builder.Default
    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.valueOf(0.0);

    @Builder.Default
    @Column(name = "review_count")
    private Integer reviewCount = 0;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "open_hours", length = 100)
    private String openHours;

    @Column(name = "price_range", length = 100)
    private String priceRange;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(name = "amenities", length = 500)
    private String amenities;

    @Builder.Default
    @Column(name = "status", length = 30)
    private String status = "ACTIVE";

    @Builder.Default
    @Column(name = "is_verified_host")
    private Boolean isVerifiedHost = false;

    @Builder.Default
    @Column(name = "ai_moderation_status", length = 50)
    private String aiModerationStatus = "APPROVED_BY_AI";

    @Column(name = "ai_moderation_note", columnDefinition = "TEXT")
    private String aiModerationNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
