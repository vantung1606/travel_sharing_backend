package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name; // Thắng cảnh, Ẩm thực, Văn hóa, Giải trí, Chữa lành

    @Column(length = 50)
    private String icon; // Icon name e.g. 'Mountain', 'Utensils'

    @Column(length = 255)
    private String description;
}
