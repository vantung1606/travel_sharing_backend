package com.wayfare.service;

import com.wayfare.dto.*;
import com.wayfare.entity.*;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.*;
import com.wayfare.repository.ItineraryMemberRepository;
import com.wayfare.repository.ItineraryExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItineraryService {

    private final ItineraryRepository itineraryRepository;
    private final ItineraryDetailRepository itineraryDetailRepository;
    private final ItineraryMemberRepository itineraryMemberRepository;
    private final ItineraryExpenseRepository itineraryExpenseRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public List<ItineraryDto> getItinerariesForUser(String email) {
        log.info("Fetching itineraries for user: {}", email);
        User user = getUserByEmailOrDefault(email);
        List<Itinerary> list = itineraryRepository.findByCreatorOrderByCreatedAtDesc(user);
        return list.stream().map(this::mapToSummaryDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ItineraryDto> getAllItineraries() {
        log.info("Fetching all itineraries across system for admin dashboard");
        List<Itinerary> list = itineraryRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return list.stream().map(this::mapToSummaryDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ItineraryDto getItineraryById(Long id, String email) {
        log.info("Fetching itinerary detail for id: {} requested by: {}", id, email);
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + id));

        ItineraryDto dto = mapToSummaryDto(itinerary);

        // Fetch details
        List<ItineraryDetail> details = itineraryDetailRepository.findByItineraryOrderByDayNumberAscVisitOrderAsc(itinerary);
        dto.setDetails(details.stream().map(this::mapDetailToDto).collect(Collectors.toList()));

        // Fetch members
        List<ItineraryMember> members = itineraryMemberRepository.findByItinerary(itinerary);
        dto.setMembers(members.stream().map(this::mapMemberToDto).collect(Collectors.toList()));

        // Fetch expenses
        List<ItineraryExpense> expenses = itineraryExpenseRepository.findByItineraryOrderByCreatedAtDesc(itinerary);
        dto.setExpenses(expenses.stream().map(this::mapExpenseToDto).collect(Collectors.toList()));

        // Calculate budget summary
        dto.setBudgetSummary(getBudgetSummary(id));

        return dto;
    }

    @Transactional
    public ItineraryDto createItinerary(ItineraryDto request, String creatorEmail) {
        log.info("Creating new itinerary: title='{}', destination='{}' by user='{}'",
                request.getTitle(), request.getDestination(), creatorEmail);

        User creator = getUserByEmailOrDefault(creatorEmail);

        Itinerary itinerary = Itinerary.builder()
                .creator(creator)
                .title(request.getTitle() != null ? request.getTitle() : "Chuyến đi mới")
                .destination(request.getDestination() != null ? request.getDestination() : "Việt Nam")
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .budgetTotal(request.getBudgetTotal() != null ? request.getBudgetTotal() : BigDecimal.valueOf(5000000))
                .coverImageUrl(request.getCoverImageUrl())
                .isAiGenerated(Boolean.TRUE.equals(request.getIsAiGenerated()))
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .build();

        Itinerary saved = itineraryRepository.save(itinerary);

        // Add creator as OWNER member
        ItineraryMember ownerMember = ItineraryMember.builder()
                .itinerary(saved)
                .user(creator)
                .role("OWNER")
                .build();
        itineraryMemberRepository.save(ownerMember);

        // Save initial details if provided (e.g. from AI trip planner)
        if (request.getDetails() != null && !request.getDetails().isEmpty()) {
            for (ItineraryDetailDto d : request.getDetails()) {
                ItineraryDetail detail = ItineraryDetail.builder()
                        .itinerary(saved)
                        .locationName(d.getLocationName())
                        .locationAddress(d.getLocationAddress())
                        .category(d.getCategory())
                        .dayNumber(d.getDayNumber() != null ? d.getDayNumber() : 1)
                        .visitOrder(d.getVisitOrder() != null ? d.getVisitOrder() : 1)
                        .startTime(d.getStartTime())
                        .estimatedCost(d.getEstimatedCost())
                        .aiTip(d.getAiTip())
                        .transitInfo(d.getTransitInfo())
                        .note(d.getNote())
                        .build();
                itineraryDetailRepository.save(detail);
            }
        }

        log.info("Successfully created itinerary id: {}", saved.getId());

        // Trigger real notification for creator
        try {
            notificationService.sendNotification(
                    creator,
                    null,
                    Boolean.TRUE.equals(request.getIsAiGenerated()) ? "AI_READY" : "SYSTEM",
                    "Lịch trình '" + saved.getTitle() + "' đã được khởi tạo và lưu trữ thành công!",
                    "/itineraries?id=" + saved.getId()
            );
            activityLogService.recordLog(
                    creator,
                    Boolean.TRUE.equals(request.getIsAiGenerated()) ? "AI_PLANNER_GENERATE" : "CREATE_ITINERARY",
                    "Tạo lịch trình mới: '" + saved.getTitle() + "' (" + saved.getDestination() + ")",
                    "127.0.0.1",
                    "Web Client"
            );
        } catch (Exception e) {
            log.warn("Failed to dispatch creation notification or activity log: {}", e.getMessage());
        }

        return getItineraryById(saved.getId(), creatorEmail);
    }

    @Transactional
    public ItineraryDto updateItinerary(Long id, ItineraryDto request, String email) {
        log.info("Updating itinerary id: {} by user: {}", id, email);
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + id));

        if (request.getTitle() != null) itinerary.setTitle(request.getTitle());
        if (request.getDestination() != null) itinerary.setDestination(request.getDestination());
        if (request.getStartDate() != null) itinerary.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) itinerary.setEndDate(request.getEndDate());
        if (request.getBudgetTotal() != null) itinerary.setBudgetTotal(request.getBudgetTotal());
        if (request.getCoverImageUrl() != null) itinerary.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getStatus() != null) itinerary.setStatus(request.getStatus());

        Itinerary saved = itineraryRepository.save(itinerary);
        log.info("Successfully updated itinerary id: {}", saved.getId());
        return getItineraryById(saved.getId(), email);
    }

    @Transactional
    public void deleteItinerary(Long id, String email) {
        log.info("Deleting itinerary id: {} by user: {}", id, email);
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + id));

        // Cascade delete child entities
        itineraryDetailRepository.deleteByItineraryId(id);
        itineraryExpenseRepository.deleteByItineraryId(id);
        itineraryRepository.delete(itinerary);
        log.info("Successfully deleted itinerary id: {}", id);
    }

    @Transactional
    public ItineraryDetailDto addDetail(Long itineraryId, ItineraryDetailDto dto, String email) {
        log.info("Adding detail stop to itinerary id: {} by user: {}", itineraryId, email);
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + itineraryId));

        ItineraryDetail detail = ItineraryDetail.builder()
                .itinerary(itinerary)
                .locationName(dto.getLocationName())
                .locationAddress(dto.getLocationAddress())
                .category(dto.getCategory())
                .dayNumber(dto.getDayNumber() != null ? dto.getDayNumber() : 1)
                .visitOrder(dto.getVisitOrder() != null ? dto.getVisitOrder() : 1)
                .startTime(dto.getStartTime())
                .estimatedCost(dto.getEstimatedCost())
                .aiTip(dto.getAiTip())
                .transitInfo(dto.getTransitInfo())
                .note(dto.getNote())
                .build();

        ItineraryDetail saved = itineraryDetailRepository.save(detail);
        return mapDetailToDto(saved);
    }

    @Transactional
    public ItineraryDetailDto updateDetail(Long detailId, ItineraryDetailDto dto, String email) {
        log.info("Updating detail stop id: {} by user: {}", detailId, email);
        ItineraryDetail detail = itineraryDetailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Detail not found with id: " + detailId));

        if (dto.getLocationName() != null) detail.setLocationName(dto.getLocationName());
        if (dto.getLocationAddress() != null) detail.setLocationAddress(dto.getLocationAddress());
        if (dto.getCategory() != null) detail.setCategory(dto.getCategory());
        if (dto.getDayNumber() != null) detail.setDayNumber(dto.getDayNumber());
        if (dto.getVisitOrder() != null) detail.setVisitOrder(dto.getVisitOrder());
        if (dto.getStartTime() != null) detail.setStartTime(dto.getStartTime());
        if (dto.getEstimatedCost() != null) detail.setEstimatedCost(dto.getEstimatedCost());
        if (dto.getAiTip() != null) detail.setAiTip(dto.getAiTip());
        if (dto.getTransitInfo() != null) detail.setTransitInfo(dto.getTransitInfo());
        if (dto.getNote() != null) detail.setNote(dto.getNote());

        ItineraryDetail saved = itineraryDetailRepository.save(detail);
        return mapDetailToDto(saved);
    }

    @Transactional
    public void deleteDetail(Long detailId, String email) {
        log.info("Deleting detail stop id: {} by user: {}", detailId, email);
        ItineraryDetail detail = itineraryDetailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Detail not found with id: " + detailId));
        itineraryDetailRepository.delete(detail);
    }

    @Transactional
    public ItineraryMemberDto addMember(Long itineraryId, String memberEmail, String role, String requesterEmail) {
        log.info("Adding member {} with role {} to itinerary {}", memberEmail, role, itineraryId);
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + itineraryId));

        User user = userRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + memberEmail));

        if (itineraryMemberRepository.existsByItineraryIdAndUserId(itineraryId, user.getId())) {
            throw new RuntimeException("Người dùng đã là thành viên của chuyến đi này.");
        }

        ItineraryMember member = ItineraryMember.builder()
                .itinerary(itinerary)
                .user(user)
                .role(role != null ? role : "VIEWER")
                .build();

        ItineraryMember saved = itineraryMemberRepository.save(member);
        return mapMemberToDto(saved);
    }

    @Transactional
    public void removeMember(Long itineraryId, Long userId, String requesterEmail) {
        log.info("Removing member userId {} from itinerary {}", userId, itineraryId);
        itineraryMemberRepository.deleteByItineraryIdAndUserId(itineraryId, userId);
    }

    @Transactional
    public ItineraryExpenseDto addExpense(Long itineraryId, ItineraryExpenseDto dto, String payerEmail) {
        log.info("Adding expense {} for itinerary {} by {}", dto.getAmount(), itineraryId, payerEmail);
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + itineraryId));

        User payer = dto.getPayerId() != null
                ? userRepository.findById(dto.getPayerId()).orElse(getUserByEmailOrDefault(payerEmail))
                : getUserByEmailOrDefault(payerEmail);

        ItineraryExpense expense = ItineraryExpense.builder()
                .itinerary(itinerary)
                .payer(payer)
                .amount(dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO)
                .category(dto.getCategory() != null ? dto.getCategory() : "Khác")
                .description(dto.getDescription())
                .receiptImageUrl(dto.getReceiptImageUrl())
                .build();

        ItineraryExpense saved = itineraryExpenseRepository.save(expense);
        return mapExpenseToDto(saved);
    }

    @Transactional
    public void deleteExpense(Long expenseId, String email) {
        log.info("Deleting expense id: {} by user: {}", expenseId, email);
        ItineraryExpense expense = itineraryExpenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + expenseId));
        itineraryExpenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public BudgetSummaryDto getBudgetSummary(Long itineraryId) {
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + itineraryId));

        BigDecimal budgetTotal = itinerary.getBudgetTotal() != null ? itinerary.getBudgetTotal() : BigDecimal.ZERO;
        BigDecimal actualTotal = itineraryExpenseRepository.getTotalExpensesByItineraryId(itineraryId);
        if (actualTotal == null) actualTotal = BigDecimal.ZERO;

        BigDecimal remaining = budgetTotal.subtract(actualTotal);
        boolean isOver = actualTotal.compareTo(budgetTotal) > 0;
        double pct = budgetTotal.compareTo(BigDecimal.ZERO) > 0
                ? actualTotal.divide(budgetTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;

        List<Object[]> categorySums = itineraryExpenseRepository.getExpensesByCategory(itineraryId);
        Map<String, BigDecimal> breakdown = new HashMap<>();
        for (Object[] row : categorySums) {
            String cat = (String) row[0];
            BigDecimal val = (BigDecimal) row[1];
            breakdown.put(cat != null ? cat : "Khác", val != null ? val : BigDecimal.ZERO);
        }

        return BudgetSummaryDto.builder()
                .itineraryId(itineraryId)
                .budgetTotal(budgetTotal)
                .actualTotal(actualTotal)
                .remainingBudget(remaining)
                .isOverBudget(isOver)
                .spentPercentage(pct)
                .breakdownByCategory(breakdown)
                .build();
    }

    // --- Private Mappers & Helpers ---

    private ItineraryDto mapToSummaryDto(Itinerary i) {
        return ItineraryDto.builder()
                .id(i.getId())
                .creatorId(i.getCreator() != null ? i.getCreator().getId() : null)
                .creatorName(i.getCreator() != null ? i.getCreator().getFullName() : "Wanderer")
                .creatorEmail(i.getCreator() != null ? i.getCreator().getEmail() : "")
                .creatorAvatar(i.getCreator() != null ? i.getCreator().getAvatarUrl() : null)
                .title(i.getTitle())
                .destination(i.getDestination())
                .startDate(i.getStartDate())
                .endDate(i.getEndDate())
                .budgetTotal(i.getBudgetTotal())
                .coverImageUrl(i.getCoverImageUrl())
                .isAiGenerated(i.getIsAiGenerated())
                .status(i.getStatus())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }

    private ItineraryDetailDto mapDetailToDto(ItineraryDetail d) {
        return ItineraryDetailDto.builder()
                .id(d.getId())
                .itineraryId(d.getItinerary() != null ? d.getItinerary().getId() : null)
                .placeId(d.getPlace() != null ? d.getPlace().getId() : null)
                .locationName(d.getLocationName())
                .locationAddress(d.getLocationAddress())
                .category(d.getCategory())
                .dayNumber(d.getDayNumber())
                .visitOrder(d.getVisitOrder())
                .startTime(d.getStartTime())
                .estimatedCost(d.getEstimatedCost())
                .aiTip(d.getAiTip())
                .transitInfo(d.getTransitInfo())
                .note(d.getNote())
                .build();
    }

    private ItineraryMemberDto mapMemberToDto(ItineraryMember m) {
        return ItineraryMemberDto.builder()
                .id(m.getId())
                .itineraryId(m.getItinerary() != null ? m.getItinerary().getId() : null)
                .userId(m.getUser() != null ? m.getUser().getId() : null)
                .fullName(m.getUser() != null ? m.getUser().getFullName() : "")
                .email(m.getUser() != null ? m.getUser().getEmail() : "")
                .avatarUrl(m.getUser() != null ? m.getUser().getAvatarUrl() : null)
                .role(m.getRole())
                .joinedAt(m.getJoinedAt())
                .build();
    }

    private ItineraryExpenseDto mapExpenseToDto(ItineraryExpense e) {
        return ItineraryExpenseDto.builder()
                .id(e.getId())
                .itineraryId(e.getItinerary() != null ? e.getItinerary().getId() : null)
                .payerId(e.getPayer() != null ? e.getPayer().getId() : null)
                .payerName(e.getPayer() != null ? e.getPayer().getFullName() : "Thành viên")
                .amount(e.getAmount())
                .category(e.getCategory())
                .description(e.getDescription())
                .receiptImageUrl(e.getReceiptImageUrl())
                .createdAt(e.getCreatedAt())
                .build();
    }

    @Transactional
    public ItineraryDto cloneItinerary(Long sourceId, String requesterEmail) {
        log.info("Cloning itinerary id: {} for user: {}", sourceId, requesterEmail);
        Itinerary source = itineraryRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found with id: " + sourceId));

        User requester = getUserByEmailOrDefault(requesterEmail);

        String clonedTitle = source.getTitle().startsWith("[Sao chép]")
                ? source.getTitle()
                : "[Sao chép] " + source.getTitle();

        Itinerary cloned = Itinerary.builder()
                .creator(requester)
                .title(clonedTitle)
                .destination(source.getDestination())
                .startDate(LocalDate.now().plusDays(7))
                .endDate(LocalDate.now().plusDays(10))
                .budgetTotal(source.getBudgetTotal())
                .coverImageUrl(source.getCoverImageUrl())
                .isAiGenerated(source.getIsAiGenerated())
                .status("ACTIVE")
                .build();

        Itinerary saved = itineraryRepository.save(cloned);

        // Add requester as OWNER
        ItineraryMember owner = ItineraryMember.builder()
                .itinerary(saved)
                .user(requester)
                .role("OWNER")
                .build();
        itineraryMemberRepository.save(owner);

        // Copy all details
        List<ItineraryDetail> sourceDetails = itineraryDetailRepository.findByItineraryOrderByDayNumberAscVisitOrderAsc(source);
        List<ItineraryDetail> clonedDetails = new ArrayList<>();
        for (ItineraryDetail d : sourceDetails) {
            ItineraryDetail copy = ItineraryDetail.builder()
                    .itinerary(saved)
                    .place(d.getPlace())
                    .locationName(d.getLocationName())
                    .locationAddress(d.getLocationAddress())
                    .category(d.getCategory())
                    .dayNumber(d.getDayNumber())
                    .visitOrder(d.getVisitOrder())
                    .startTime(d.getStartTime())
                    .estimatedCost(d.getEstimatedCost())
                    .aiTip(d.getAiTip())
                    .transitInfo(d.getTransitInfo())
                    .note(d.getNote())
                    .build();
            clonedDetails.add(itineraryDetailRepository.save(copy));
        }

        try {
            notificationService.sendNotification(
                    requester,
                    null,
                    "SYSTEM",
                    "Bạn đã sao chép thành công chuyến đi: " + cloned.getTitle(),
                    "/itineraries"
            );
            activityLogService.recordLog(
                    requester,
                    "CLONE_ITINERARY",
                    "Sao chép lịch trình: " + source.getTitle(),
                    "127.0.0.1",
                    "Web Client"
            );
        } catch (Exception e) {
            log.warn("Non-critical error logging clone activity: {}", e.getMessage());
        }

        ItineraryDto dto = mapToSummaryDto(saved);
        dto.setDetails(clonedDetails.stream().map(this::mapDetailToDto).collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public ItineraryDto aiQuickGenerate(AiTripGenerateRequest request, String requesterEmail) {
        log.info("AI Quick generating trip for dest='{}', budget='{}', style='{}', dur='{}' by='{}'",
                request.getDestination(), request.getBudget(), request.getStyle(), request.getDuration(), requesterEmail);

        User requester = getUserByEmailOrDefault(requesterEmail);
        String destination = (request.getDestination() != null && !request.getDestination().isBlank())
                ? request.getDestination().trim()
                : "Đà Nẵng & Hội An";

        int daysCount = 3;
        if ("2d1n".equalsIgnoreCase(request.getDuration())) daysCount = 2;
        else if ("4d3n".equalsIgnoreCase(request.getDuration())) daysCount = 4;
        else if ("5d4n".equalsIgnoreCase(request.getDuration())) daysCount = 5;

        BigDecimal totalBudget;
        if (request.getBudgetAmount() != null) {
            totalBudget = request.getBudgetAmount();
        } else if ("luxury".equalsIgnoreCase(request.getBudget())) {
            totalBudget = BigDecimal.valueOf(8500000);
        } else if ("budget".equalsIgnoreCase(request.getBudget())) {
            totalBudget = BigDecimal.valueOf(2500000);
        } else {
            totalBudget = BigDecimal.valueOf(4500000);
        }

        String styleLabel = "Nghỉ dưỡng & Ẩm thực";
        if ("photo".equalsIgnoreCase(request.getStyle())) styleLabel = "Sống ảo & Văn hóa";
        else if ("nature".equalsIgnoreCase(request.getStyle())) styleLabel = "Trekking & Thiên nhiên";
        else if ("family".equalsIgnoreCase(request.getStyle())) styleLabel = "Gia đình & Thư giãn";

        String coverImage = "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1200&q=85";
        String lowerDest = destination.toLowerCase();
        if (lowerDest.contains("đà nẵng") || lowerDest.contains("hội an")) {
            coverImage = "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=1200&q=85";
        } else if (lowerDest.contains("đà lạt")) {
            coverImage = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=85";
        } else if (lowerDest.contains("phú quốc")) {
            coverImage = "https://images.unsplash.com/photo-1589394815804-964ed0be2eb5?auto=format&fit=crop&w=1200&q=85";
        } else if (lowerDest.contains("sapa") || lowerDest.contains("sa pa") || lowerDest.contains("mù cang")) {
            coverImage = "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1200&q=85";
        } else if (lowerDest.contains("ninh bình")) {
            coverImage = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=85";
        }

        LocalDate startDate = LocalDate.now().plusDays(5);
        LocalDate endDate = startDate.plusDays(daysCount - 1);

        Itinerary itinerary = Itinerary.builder()
                .creator(requester)
                .title(destination + ": Lộ Trình AI Thông Minh (" + daysCount + "N" + (daysCount - 1) + "Đ)")
                .destination(destination)
                .startDate(startDate)
                .endDate(endDate)
                .budgetTotal(totalBudget)
                .coverImageUrl(coverImage)
                .isAiGenerated(true)
                .status("ACTIVE")
                .build();

        Itinerary saved = itineraryRepository.save(itinerary);

        ItineraryMember owner = ItineraryMember.builder()
                .itinerary(saved)
                .user(requester)
                .role("OWNER")
                .build();
        itineraryMemberRepository.save(owner);

        List<ItineraryDetail> generatedDetails = new ArrayList<>();
        for (int day = 1; day <= daysCount; day++) {
            generatedDetails.add(itineraryDetailRepository.save(ItineraryDetail.builder()
                    .itinerary(saved)
                    .dayNumber(day)
                    .visitOrder(1)
                    .startTime(LocalTime.of(8, 30))
                    .locationName(day == 1 ? "Ăn sáng đặc sản địa phương & Cà phê phin" : (day == 2 ? "Đón bình minh & Thắng cảnh biểu tượng" : "Dạo chợ địa phương & Mua đặc sản làm quà"))
                    .category(day == 1 ? "Ẩm thực" : (day == 2 ? "Thắng cảnh" : "Mua sắm"))
                    .estimatedCost(BigDecimal.valueOf(150000))
                    .aiTip("AI gợi ý khởi hành lúc 8:30 để thời tiết mát mẻ và vắng khách hơn.")
                    .transitInfo("Taxi / Xe máy ~ 10 phút")
                    .note("Gu trải nghiệm: " + styleLabel)
                    .build()));

            generatedDetails.add(itineraryDetailRepository.save(ItineraryDetail.builder()
                    .itinerary(saved)
                    .dayNumber(day)
                    .visitOrder(2)
                    .startTime(LocalTime.of(14, 0))
                    .locationName(day == 1 ? "Check-in khách sạn & Khám phá danh lam thắng cảnh" : (day == 2 ? "Trải nghiệm văn hoá & Hoạt động dã ngoại outdoor" : "Check-out & Thưởng thức trà chiều ngắm cảnh"))
                    .category("Khám phá")
                    .estimatedCost(BigDecimal.valueOf(350000))
                    .aiTip("Đặt vé trước qua đối tác Wayfare để tiết kiệm 15% chi phí.")
                    .transitInfo("Đi bộ hoặc xe ôm công nghệ")
                    .note("Điểm chụp hình đẹp chuẩn phong cách " + styleLabel)
                    .build()));

            generatedDetails.add(itineraryDetailRepository.save(ItineraryDetail.builder()
                    .itinerary(saved)
                    .dayNumber(day)
                    .visitOrder(3)
                    .startTime(LocalTime.of(18, 30))
                    .locationName(day == 1 ? "Phố ẩm thực đêm & Thưởng thức hải sản/đặc sản" : (day == 2 ? "Chill quán cà phê acoustic / Bar ngắm hoàng hôn" : "Tiệc tối chia tay & Chuẩn bị hành lý"))
                    .category("Ẩm thực & Giải trí")
                    .estimatedCost(BigDecimal.valueOf(450000))
                    .aiTip("Nên đặt bàn trước khung giờ cao điểm để có vị trí ngắm view đẹp nhất.")
                    .transitInfo("Đi dạo bộ trung tâm")
                    .note("Trải nghiệm không khí đêm rực rỡ")
                    .build()));
        }

        try {
            notificationService.sendNotification(
                    requester,
                    null,
                    "SYSTEM",
                    "WanderAI đã khởi tạo thành công lịch trình: " + saved.getTitle() + "!",
                    "/itineraries"
            );
            activityLogService.recordLog(
                    requester,
                    "AI_GENERATE_ITINERARY",
                    "Khởi tạo lộ trình AI: " + saved.getTitle(),
                    "127.0.0.1",
                    "Web Client"
            );
        } catch (Exception e) {
            log.warn("Non-critical error logging AI generation: {}", e.getMessage());
        }

        ItineraryDto dto = mapToSummaryDto(saved);
        dto.setDetails(generatedDetails.stream().map(this::mapDetailToDto).collect(Collectors.toList()));
        return dto;
    }

    private User getUserByEmailOrDefault(String email) {
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email).orElseGet(this::getDefaultAdminUser);
        }
        return getDefaultAdminUser();
    }

    private User getDefaultAdminUser() {
        return userRepository.findByEmail("admin@gmail.com")
                .orElseGet(() -> userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("No default user found in database")));
    }
}
