package com.wayfare.modules.place.service;

import com.wayfare.modules.admin.service.ActivityLogService;
import com.wayfare.common.dto.PagedResponse;
import com.wayfare.modules.place.dto.CreatePlaceRequest;
import com.wayfare.modules.place.dto.PlaceDto;
import com.wayfare.modules.place.dto.UpdatePlaceRequest;
import com.wayfare.entity.Place;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.PlaceRepository;
import com.wayfare.repository.UserRepository;
import com.wayfare.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wayfare.entity.PlaceImage;
import com.wayfare.repository.PlaceImageRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceImageRepository placeImageRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final AiPlaceModerationService aiPlaceModerationService;
    private final NotificationService notificationService;
    private final GeocodingService geocodingService;

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
            } else if (status.equalsIgnoreCase("APPROVED") || status.equalsIgnoreCase("ACTIVE")) {
                cleanStatus = "ACTIVE";
            } else if (!status.equalsIgnoreCase("Tất cả")) {
                cleanStatus = status.trim();
            }
        }
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        List<Place> places = placeRepository.searchPlaces(cleanStatus, cleanCity, cleanCategory, cleanKeyword);
        return places.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Lấy danh sách địa điểm có phân trang (opt-in qua query param page/size).
     * Chỉ map DTO cho đúng trang cần trả về, giảm tải payload và số câu query.
     */
    @Transactional(readOnly = true)
    public PagedResponse<PlaceDto> getPlacesPaged(String city, String category, String status, String keyword,
                                                  int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        log.info("Fetching paged places - page: {}, size: {}, city: {}, category: {}, status: {}, keyword: {}",
                safePage, safeSize, city, category, status, keyword);

        String cleanCity = (city != null && !city.isBlank() && !city.equalsIgnoreCase("Tất cả")) ? city.trim() : null;
        String cleanCategory = (category != null && !category.isBlank() && !category.equalsIgnoreCase("Tất cả")) ? category.trim() : null;
        String cleanStatus = "ACTIVE";
        if (status != null && !status.isBlank()) {
            if (status.equalsIgnoreCase("ALL")) {
                cleanStatus = null;
            } else if (status.equalsIgnoreCase("APPROVED") || status.equalsIgnoreCase("ACTIVE")) {
                cleanStatus = "ACTIVE";
            } else if (!status.equalsIgnoreCase("Tất cả")) {
                cleanStatus = status.trim();
            }
        }
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Place> result = placeRepository.searchPlacesPaged(cleanStatus, cleanCity, cleanCategory, cleanKeyword, pageable);

        List<PlaceDto> items = result.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return PagedResponse.<PlaceDto>builder()
                .items(items)
                .page(safePage)
                .size(safeSize)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
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

    /**
     * Quy trình Badge Xác thực: admin/moderator cấp hoặc gỡ cờ is_verified_host.
     * Mọi thao tác đều ghi nhật ký kiểm toán (audit trail).
     */
    @Transactional
    public PlaceDto setVerified(Long id, boolean verified, String adminEmail) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Địa điểm không tồn tại với ID: " + id));
        place.setIsVerifiedHost(verified);
        Place saved = placeRepository.save(place);
        log.info("Place ID: {} verified={} set by {}", id, verified, adminEmail);

        try {
            activityLogService.recordLog(saved.getOwner(), verified ? "PLACE_VERIFY" : "PLACE_UNVERIFY",
                    (verified ? "Cấp badge Xác thực cho địa điểm: " : "Gỡ badge Xác thực của địa điểm: ")
                            + saved.getName() + " (#" + id + ") bởi " + adminEmail,
                    "127.0.0.1", "Admin Portal");
        } catch (Exception e) {
            log.warn("Failed to record verify badge audit log: {}", e.getMessage());
        }
        return toDto(saved);
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

        // Quyết định trạng thái khởi tạo và thẩm định AI:
        String initialStatus;
        boolean isVerifiedHost = false;
        String aiModerationStatus;
        String aiModerationNote;

        if (isAdminOrMod && "ACTIVE".equalsIgnoreCase(request.getStatus())) {
            initialStatus = "ACTIVE";
            isVerifiedHost = true;
            aiModerationStatus = "APPROVED_BY_ADMIN";
            aiModerationNote = "Được khởi tạo trực tiếp bởi Quản trị viên";
        } else {
            // Thẩm định bằng AI WanderAI:
            // 1. Kiểm tra trùng lặp địa chỉ cụ thể & tên địa điểm trên toàn bộ CSDL.
            // 2. Kiểm tra tính có thật của địa điểm và địa chỉ.
            // 3. Nếu AI từ chối (trùng, không có thật, nghi vấn) -> KHÔNG xóa mà chuyển toàn bộ sang Admin kiểm duyệt (PENDING_APPROVAL).
            AiPlaceModerationService.PlaceModerationResult aiResult = aiPlaceModerationService.moderatePlace(request);
            if (aiResult.isApproved()) {
                initialStatus = "ACTIVE";
                isVerifiedHost = true;
                aiModerationStatus = "APPROVED_BY_AI";
                aiModerationNote = aiResult.getNote();
            } else {
                initialStatus = "PENDING_APPROVAL";
                isVerifiedHost = false;
                aiModerationStatus = aiResult.getModerationStatus();
                aiModerationNote = aiResult.getNote();

                // Gửi thông báo đến Quản trị viên vào hàng đợi duyệt (loại trừ chính người tạo nếu là Admin)
                try {
                    notificationService.notifyAdmins(
                            "PLACE_MODERATION",
                            "Địa điểm mới '" + request.getName() + "' cần thẩm định: " + aiResult.getNote(),
                            "/admin/places",
                            owner
                    );
                } catch (Exception e) {
                    log.warn("Failed to notify admins for place moderation: {}", e.getMessage());
                }
            }
        }

        // Tự động tìm tọa độ GPS từ địa chỉ nếu chưa có tọa độ
        BigDecimal lat = request.getLatitude();
        BigDecimal lng = request.getLongitude();
        if (lat == null || lng == null || lat.compareTo(BigDecimal.ZERO) == 0) {
            GeocodingService.Coordinates coords = geocodingService.geocode(request.getAddress(), request.getCity());
            if (coords != null) {
                lat = coords.latitude();
                lng = coords.longitude();
                log.info("Auto-geocoded place '{}' at creation to lat: {}, lng: {}", request.getName(), lat, lng);
            }
        }

        Place place = Place.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(request.getCity())
                .latitude(lat)
                .longitude(lng)
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
                .aiModerationStatus(aiModerationStatus)
                .aiModerationNote(aiModerationNote)
                .build();

        Place saved = placeRepository.save(place);
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            for (String imgUrl : request.getImages()) {
                if (imgUrl != null && !imgUrl.isBlank()) {
                    PlaceImage pi = PlaceImage.builder()
                            .place(saved)
                            .imageUrl(imgUrl.trim())
                            .isPrimary(false)
                            .build();
                    placeImageRepository.save(pi);
                    saved.getImages().add(pi);
                }
            }
        }
        log.info("Successfully created place ID: {} with status: {}, aiStatus: {} by user: {}",
                saved.getId(), initialStatus, aiModerationStatus, userEmail);

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
        place.setAiModerationStatus("APPROVED_BY_ADMIN");
        place.setAiModerationNote("Được Ban Quản Trị phê duyệt chính thức.");

        // BẮT BUỘC: Đảm bảo có tọa độ GPS trước khi xuất hiện trên bản đồ cho du khách
        if (place.getLatitude() == null || place.getLongitude() == null || place.getLatitude().compareTo(BigDecimal.ZERO) == 0) {
            GeocodingService.Coordinates coords = geocodingService.geocode(place.getAddress(), place.getCity());
            if (coords != null) {
                place.setLatitude(coords.latitude());
                place.setLongitude(coords.longitude());
                log.info("Auto-geocoded place ID {} upon approval to lat: {}, lng: {}", id, coords.latitude(), coords.longitude());
            }
        }

        Place saved = placeRepository.save(place);
        log.info("Place ID: {} approved successfully and marked as verified host with GPS ({}, {})",
                id, saved.getLatitude(), saved.getLongitude());

        if (saved.getOwner() != null) {
            try {
                notificationService.sendNotification(
                        saved.getOwner(),
                        null,
                        "PLACE_APPROVED",
                        "Địa điểm '" + saved.getName() + "' của bạn đã được Ban Quản Trị phê duyệt chính thức!",
                        "/places/" + saved.getId()
                );
            } catch (Exception e) {
                log.warn("Failed to notify place owner of approval: {}", e.getMessage());
            }
        }
        return toDto(saved);
    }

    /**
     * Tự động quét và đồng bộ tọa độ GPS cho toàn bộ địa điểm còn thiếu trong CSDL
     */
    @Transactional
    public int geocodeAllMissingPlaces() {
        log.info("Starting batch geocoding for all places missing GPS coordinates");
        List<Place> allPlaces = placeRepository.findAll();
        int fixedCount = 0;
        for (Place p : allPlaces) {
            if (p.getLatitude() == null || p.getLongitude() == null || p.getLatitude().compareTo(BigDecimal.ZERO) == 0) {
                GeocodingService.Coordinates coords = geocodingService.geocode(p.getAddress(), p.getCity());
                if (coords != null) {
                    p.setLatitude(coords.latitude());
                    p.setLongitude(coords.longitude());
                    placeRepository.save(p);
                    fixedCount++;
                }
            }
        }
        log.info("Batch geocoding completed: {} places updated with GPS coordinates", fixedCount);
        return fixedCount;
    }

    @Transactional
    public PlaceDto rejectPlace(Long id, String reason) {
        log.info("Admin rejecting place ID: {} for reason: {}", id, reason);
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm với ID: " + id));
        place.setStatus("REJECTED");
        place.setIsVerifiedHost(false);
        place.setAiModerationStatus("REJECTED_BY_ADMIN");
        place.setAiModerationNote(reason != null && !reason.isBlank() ? reason : "Bị Ban Quản Trị từ chối");
        Place saved = placeRepository.save(place);
        log.info("Place ID: {} rejected successfully", id);

        if (saved.getOwner() != null) {
            try {
                notificationService.sendNotification(
                        saved.getOwner(),
                        null,
                        "PLACE_REJECTED",
                        "Địa điểm '" + saved.getName() + "' của bạn không được phê duyệt. Lý do: " + saved.getAiModerationNote(),
                        null
                );
            } catch (Exception e) {
                log.warn("Failed to notify place owner of rejection: {}", e.getMessage());
            }
        }
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
    public PlaceDto updatePlaceByAdmin(Long id, UpdatePlaceRequest request, String adminEmail) {
        log.info("Admin {} updating place ID: {}", adminEmail, id);
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm với ID: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            place.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            place.setDescription(request.getDescription().trim());
        }
        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            place.setAddress(request.getAddress().trim());
        }
        if (request.getCity() != null && !request.getCity().isBlank()) {
            place.setCity(request.getCity().trim());
        }
        if (request.getLatitude() != null) {
            place.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            place.setLongitude(request.getLongitude());
        } else if (place.getLatitude() == null || place.getLongitude() == null) {
            try {
                GeocodingService.Coordinates coords = geocodingService.geocode(place.getAddress(), place.getCity());
                if (coords != null) {
                    place.setLatitude(coords.latitude());
                    place.setLongitude(coords.longitude());
                }
            } catch (Exception e) {
                log.warn("Auto-geocoding failed during place update: {}", e.getMessage());
            }
        }

        if (request.getTicketPrice() != null) {
            place.setTicketPrice(request.getTicketPrice());
        }
        if (request.getPriceRange() != null) {
            place.setPriceRange(request.getPriceRange().trim());
        }
        if (request.getOpenHours() != null) {
            place.setOpenHours(request.getOpenHours().trim());
        }
        if (request.getPhoneNumber() != null) {
            place.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getCategoryName() != null) {
            place.setCategoryName(request.getCategoryName().trim());
        }
        if (request.getAmenities() != null) {
            place.setAmenities(request.getAmenities().trim());
        }
        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().isBlank()) {
            place.setCoverImageUrl(request.getCoverImageUrl().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            place.setStatus(request.getStatus().trim());
        }
        if (request.getIsVerifiedHost() != null) {
            place.setIsVerifiedHost(request.getIsVerifiedHost());
        }

        // Cập nhật danh sách ảnh chi tiết (nếu request gửi lên mảng images)
        if (request.getImages() != null) {
            placeImageRepository.deleteByPlaceId(id);
            List<PlaceImage> newImages = new java.util.ArrayList<>();
            boolean hasPrimary = false;
            for (int i = 0; i < request.getImages().size(); i++) {
                String imgUrl = request.getImages().get(i);
                if (imgUrl != null && !imgUrl.isBlank()) {
                    boolean isPrimary = !hasPrimary;
                    hasPrimary = true;
                    newImages.add(PlaceImage.builder()
                            .place(place)
                            .imageUrl(imgUrl.trim())
                            .isPrimary(isPrimary)
                            .caption(place.getName() + " #" + (i + 1))
                            .build());
                }
            }
            if (!newImages.isEmpty()) {
                placeImageRepository.saveAll(newImages);
            }
        }

        Place saved = placeRepository.save(place);

        // Ghi nhận Audit Activity Log
        try {
            activityLogService.recordLog(
                    saved.getOwner(),
                    "PLACE_UPDATE",
                    "Admin " + adminEmail + " đã cập nhật thông tin địa điểm: " + saved.getName() + " (#" + id + ")",
                    "127.0.0.1",
                    "Admin Portal"
            );
        } catch (Exception e) {
            log.warn("Failed to log activity for place update: {}", e.getMessage());
        }

        log.info("Place ID {} updated successfully by admin {}", id, adminEmail);
        return toDto(saved);
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

        // Unlink and cascade delete dependent foreign key references to prevent DataIntegrityViolationException
        placeRepository.unlinkItineraryDetails(id);
        placeRepository.deleteReviewsByPlaceId(id);
        placeRepository.deleteSavedPlacesByPlaceId(id);
        placeRepository.deletePlaceImagesByPlaceId(id);

        placeRepository.delete(place);
        log.info("Successfully deleted place ID: {}", id);
    }

    public PlaceDto toDto(Place place) {
        if (place == null) return null;

        User owner = place.getOwner();
        List<String> imageUrls = new ArrayList<>();
        if (place.getImages() != null && !place.getImages().isEmpty()) {
            for (PlaceImage pi : place.getImages()) {
                if (pi != null && pi.getImageUrl() != null && !pi.getImageUrl().isBlank()) {
                    imageUrls.add(pi.getImageUrl().trim());
                }
            }
        }
        if (imageUrls.isEmpty() && place.getCoverImageUrl() != null && !place.getCoverImageUrl().isBlank()) {
            imageUrls.add(place.getCoverImageUrl().trim());
        }

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
                .images(imageUrls)
                .phoneNumber(place.getPhoneNumber())
                .openHours(place.getOpenHours())
                .priceRange(place.getPriceRange())
                .categoryName(place.getCategoryName())
                .amenities(place.getAmenities())
                .status(place.getStatus() != null ? place.getStatus() : "ACTIVE")
                .isVerifiedHost(place.getIsVerifiedHost() != null ? place.getIsVerifiedHost() : false)
                .aiModerationStatus(place.getAiModerationStatus() != null ? place.getAiModerationStatus() : "APPROVED_BY_AI")
                .aiModerationNote(place.getAiModerationNote())
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .ownerHandle(owner != null ? owner.getHandle() : null)
                .ownerAvatar(owner != null ? owner.getAvatarUrl() : null)
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }
}


