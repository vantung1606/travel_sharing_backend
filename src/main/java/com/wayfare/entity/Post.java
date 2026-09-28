package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id")
    private Itinerary itinerary;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "location_tag", length = 150)
    private String locationTag;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Builder.Default
    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Builder.Default
    @Column(name = "comment_count")
    private Integer commentCount = 0;

    @Builder.Default
    @Column(name = "status", length = 30)
    private String status = "ACTIVE"; // ACTIVE, PENDING_REPORT, HIDDEN, REMOVED

    @Column(name = "category", length = 100)
    private String category; // e.g., "Nguy cơ an toàn & Pháp luật", "Spam Thương Mại"

    @Builder.Default
    @Column(name = "ai_safety_score")
    private Integer aiSafetyScore = 98; // 0 to 100

    @Column(name = "ai_flag_reason", columnDefinition = "TEXT")
    private String aiFlagReason;

    @Builder.Default
    @Column(name = "reports_count")
    private Integer reportsCount = 0;

    @Column(name = "report_reason", columnDefinition = "TEXT")
    private String reportReason;

    @Column(name = "badge_text", length = 100)
    private String badgeText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
