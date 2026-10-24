package com.wayfare.modules.place.service;

import com.wayfare.modules.admin.service.ActivityLogService;
import com.wayfare.common.dto.PagedResponse;
import com.wayfare.modules.place.dto.CreatePlaceRequest;
import com.wayfare.modules.place.dto.PlaceDto;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final AiPlaceModerationService aiPlaceModerationService;
    private final NotificationService notificationService;

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
                .aiModerationStatus(aiModerationStatus)
                .aiModerationNote(aiModerationNote)
                .build();

        Place saved = placeRepository.save(place);
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
        Place saved = placeRepository.save(place);
        log.info("Place ID: {} approved successfully and marked as verified host", id);

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


