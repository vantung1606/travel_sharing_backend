package com.wayfare.service;

import com.wayfare.dto.CreatePlaceRequest;
import com.wayfare.dto.PlaceDto;
import com.wayfare.entity.Place;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.PlaceRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PlaceDto> getAllPlaces(String city, String category, String status, String keyword) {
        log.info("Fetching places with filters - city: {}, category: {}, status: {}, keyword: {}", city, category, status, keyword);
        String cleanCity = (city != null && !city.isBlank() && !city.equalsIgnoreCase("Tất cả")) ? city.trim() : null;
        String cleanCategory = (category != null && !category.isBlank() && !category.equalsIgnoreCase("Tất cả")) ? category.trim() : null;
        
        // Mặc định chỉ hiển thị các địa điểm đã ACTIVE cho người dùng thông thường, trừ khi có filter rõ ràng hoặc ALL
        String cleanStatus = "ACTIVE";
        if (status != null && !status.isBlank()) {
            if (status.equalsIgnoreCase("ALL")) {
                cleanStatus = null;
            } else if (!status.equalsIgnoreCase("Tất cả")) {
                cleanStatus = status.trim();
            }
        }
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        List<Place> places = placeRepository.searchPlaces(cleanStatus, cleanCity, cleanCategory, cleanKeyword);
        return places.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PlaceDto> getPendingPlaces() {
        log.info("Fetching pending places for admin approval");
        return placeRepository.findByStatus("PENDING_APPROVAL")
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlaceDto getPlaceById(Long id) {
        log.info("Fetching place detail by id: {}", id);
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Địa điểm không tồn tại với ID: " + id));
        return toDto(place);
    }

    @Transactional
    public PlaceDto createPlace(CreatePlaceRequest request, String userEmail) {
        log.info("Creating new place: {} by user: {}", request.getName(), userEmail);
        User owner = null;
        if (userEmail != null && !userEmail.isBlank()) {
            owner = userRepository.findByEmail(userEmail).orElse(null);
        }

        boolean isAdminOrMod = false;
        if (owner != null && owner.getRoles() != null) {
            isAdminOrMod = owner.getRoles().stream()
                    .anyMatch(r -> r.getName().equalsIgnoreCase("ROLE_ADMIN") || r.getName().equalsIgnoreCase("ROLE_MODERATOR"));
        }

        // Nếu là Admin/Moderator tạo: Duyệt ngay ACTIVE + Host uy tín.
        // Nếu là Người dùng thường / Cơ sở kinh doanh gửi: PENDING_APPROVAL chờ duyệt
        String initialStatus = isAdminOrMod ? "ACTIVE" : "PENDING_APPROVAL";
        boolean isVerifiedHost = isAdminOrMod;

        Place place = Place.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(request.getCity())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .ticketPrice(request.getTicketPrice() != null ? request.getTicketPrice() : BigDecimal.ZERO)
                .priceRange(request.getPriceRange())
                .openHours(request.getOpenHours())
                .phoneNumber(request.getPhoneNumber())
                .categoryName(request.getCategoryName() != null ? request.getCategoryName() : "Tọa độ bản địa")
                .amenities(request.getAmenities())
                .coverImageUrl(request.getCoverImageUrl() != null && !request.getCoverImageUrl().isBlank()
                        ? request.getCoverImageUrl()
                        : "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80")
                .owner(owner)
                .averageRating(BigDecimal.valueOf(5.0))
                .reviewCount(0)
                .status(initialStatus)
                .isVerifiedHost(isVerifiedHost)
                .build();

        Place saved = placeRepository.save(place);
        log.info("Successfully created place ID: {} with status: {} by user: {}", saved.getId(), initialStatus, userEmail);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PlaceDto> getMyPlaces(String userEmail) {
        log.info("Fetching places owned by user: {}", userEmail);
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
        return placeRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlaceDto approvePlace(Long id) {
        log.info("Admin approving place ID: {}", id);
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm với ID: " + id));
        place.setStatus("ACTIVE");
        place.setIsVerifiedHost(true);
        Place saved = placeRepository.save(place);
        log.info("Place ID: {} approved successfully and marked as verified host", id);
        return toDto(saved);
    }

    @Transactional
    public PlaceDto rejectPlace(Long id, String reason) {
        log.info("Admin rejecting place ID: {} for reason: {}", id, reason);
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm với ID: " + id));
        place.setStatus("REJECTED");
        place.setIsVerifiedHost(false);
        Place saved = placeRepository.save(place);
        log.info("Place ID: {} rejected successfully", id);
        return toDto(saved);
    }

    @Transactional
    public PlaceDto updatePlaceStatus(Long id, String status) {
        log.info("Updating status of place ID: {} to {}", id, status);
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm với ID: " + id));
        place.setStatus(status);
        return toDto(placeRepository.save(place));
    }

    @Transactional
    public void deletePlace(Long id, String userEmail) {
        log.info("Request to delete place ID: {} by user: {}", id, userEmail);
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm với ID: " + id));

        if (userEmail != null && !userEmail.isBlank()) {
            User user = userRepository.findByEmail(userEmail).orElse(null);
            if (user != null) {
                boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(r -> r.getName().equalsIgnoreCase("ROLE_ADMIN"));
                boolean isOwner = place.getOwner() != null && place.getOwner().getId().equals(user.getId());
                if (!isAdmin && !isOwner) {
                    throw new IllegalArgumentException("Bạn không có quyền xóa địa điểm này");
                }
            }
        }

        placeRepository.delete(place);
        log.info("Successfully deleted place ID: {}", id);
    }

    public PlaceDto toDto(Place place) {
        if (place == null) return null;

        User owner = place.getOwner();
        return PlaceDto.builder()
                .id(place.getId())
                .name(place.getName())
                .description(place.getDescription())
                .address(place.getAddress())
                .city(place.getCity())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .ticketPrice(place.getTicketPrice())
                .averageRating(place.getAverageRating())
                .reviewCount(place.getReviewCount())
                .coverImageUrl(place.getCoverImageUrl())
                .phoneNumber(place.getPhoneNumber())
                .openHours(place.getOpenHours())
                .priceRange(place.getPriceRange())
                .categoryName(place.getCategoryName())
                .amenities(place.getAmenities())
                .status(place.getStatus() != null ? place.getStatus() : "ACTIVE")
                .isVerifiedHost(place.getIsVerifiedHost() != null ? place.getIsVerifiedHost() : false)
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .ownerHandle(owner != null ? owner.getHandle() : null)
                .ownerAvatar(owner != null ? owner.getAvatarUrl() : null)
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }
}
