package com.wayfare.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "itinerary_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "location_name", length = 150)
    private String locationName; // In case of custom stop not in place DB

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber; // Day 1, Day 2...

    @Column(name = "visit_order", nullable = false)
    private Integer visitOrder; // Order inside the day

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(columnDefinition = "TEXT")
    private String note;
}
