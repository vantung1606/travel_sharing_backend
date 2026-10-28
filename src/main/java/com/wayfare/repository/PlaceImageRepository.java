package com.wayfare.repository;

import com.wayfare.entity.PlaceImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {
    List<PlaceImage> findByPlaceIdOrderByIdAsc(Long placeId);
    void deleteByPlaceId(Long placeId);
}
