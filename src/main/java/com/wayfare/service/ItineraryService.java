package com.wayfare.service;

import com.wayfare.dto.*;
import com.wayfare.entity.*;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Transactional(readOnly = true)
    public List<ItineraryDto> getItinerariesForUser(String email) {
        log.info("Fetching itineraries for user: {}", email);
        User user = getUserByEmailOrDefault(email);
        List<Itinerary> list = itineraryRepository.findByCreatorOrderByCreatedAtDesc(user);
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
