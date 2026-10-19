package com.wayfare.modules.place.service;

import com.wayfare.entity.Place;
import com.wayfare.entity.SavedPlace;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.PlaceRepository;
import com.wayfare.repository.SavedPlaceRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Danh sách "muốn đi" lưu TRÊN SERVER (bảng saved_places, unique user+place)
 * — thay vì chỉ lưu theo từng thiết bị như trước. Web và app mobile cùng
 * nhìn thấy 1 danh sách duy nhất, đúng tinh thần travel-sharing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavedPlaceService {

    private final SavedPlaceRepository savedPlaceRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    private User requireUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để đồng bộ danh sách yêu thích");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + email));
    }

    @Transactional(readOnly = true)
    public List<Place> getSavedPlaces(String email) {
        User user = requireUser(email);
        return savedPlaceRepository.findAllByUserIdWithPlace(user.getId())
                .stream()
                .map(SavedPlace::getPlace)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addSavedPlace(String email, Long placeId) {
        User user = requireUser(email);
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Địa điểm không tồn tại với ID: " + placeId));
        if (savedPlaceRepository.existsByUserIdAndPlaceId(user.getId(), placeId)) {
            log.info("Place {} already saved by user {} — skip duplicate", placeId, email);
            return;
        }
        savedPlaceRepository.save(SavedPlace.builder()
                .user(user)
                .place(place)
                .build());
        log.info("User '{}' saved place {} to wishlist", email, placeId);
    }

    @Transactional
    public void removeSavedPlace(String email, Long placeId) {
        User user = requireUser(email);
        savedPlaceRepository.deleteByUserIdAndPlaceId(user.getId(), placeId);
        log.info("User '{}' removed place {} from wishlist", email, placeId);
    }

    @Transactional(readOnly = true)
    public long countSavedByUser(String email) {
        User user = requireUser(email);
        return savedPlaceRepository.findAllByUserIdWithPlace(user.getId()).size();
    }
}
