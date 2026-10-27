package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 30) // POST, PLACE, USER, REVIEW
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_title", length = 255)
    private String targetTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(nullable = false, length = 150)
    private String category; // e.g. "Spam / Lừa đảo", "Thông tin sai lệch", "Nội dung độc hại", "Quấy rối / Đả kích"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence_image_url", length = 500)
    private String evidenceImageUrl;

    @Builder.Default
    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDING"; // PENDING, RESOLVED, DISMISSED

    @Column(name = "moderator_notes", columnDefinition = "TEXT")
    private String moderatorNotes;

    @Column(name = "action_taken", length = 100)
    private String actionTaken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
