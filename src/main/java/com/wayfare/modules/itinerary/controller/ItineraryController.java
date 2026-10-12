package com.wayfare.modules.itinerary.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.itinerary.dto.*;
import com.wayfare.modules.itinerary.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ItineraryController {

    private final ItineraryService itineraryService;

    private String extractEmail(Authentication authentication, String queryEmail) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return (queryEmail != null && !queryEmail.isBlank()) ? queryEmail.trim() : null;
    }

    private String requireAuthEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để thực hiện thao tác này!");
        }
        return authentication.getName();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ItineraryDto>>> getMyItineraries(
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String effectiveEmail = extractEmail(authentication, email);
        log.info("REST request to get itineraries for: {}", effectiveEmail);
        List<ItineraryDto> list = itineraryService.getItinerariesForUser(effectiveEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch trình thành công", list));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ItineraryDto>>> getAllItineraries() {
        log.info("REST request to get all system itineraries");
        List<ItineraryDto> list = itineraryService.getAllItineraries();
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ lịch trình hệ thống thành công", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ItineraryDto>> getItineraryById(
            @PathVariable Long id,
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String effectiveEmail = extractEmail(authentication, email);
        log.info("REST request to get itinerary id: {} by: {}", id, effectiveEmail);
        ItineraryDto dto = itineraryService.getItineraryById(id, effectiveEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết lịch trình thành công", dto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItineraryDto>> createItinerary(
            @RequestBody ItineraryDto request,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to create itinerary: '{}' for: {}", request.getTitle(), email);
        ItineraryDto created = itineraryService.createItinerary(request, email);
        return ResponseEntity.ok(ApiResponse.success("Tạo lịch trình chuyến đi thành công!", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ItineraryDto>> updateItinerary(
            @PathVariable Long id,
            @RequestBody ItineraryDto request,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to update itinerary id: {} by: {}", id, email);
        ItineraryDto updated = itineraryService.updateItinerary(id, request, email);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật lịch trình thành công!", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteItinerary(
            @PathVariable Long id,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to delete itinerary id: {} by: {}", id, email);
        itineraryService.deleteItinerary(id, email);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa lịch trình chuyến đi thành công", null));
    }

    @PostMapping("/{id}/details")
    public ResponseEntity<ApiResponse<ItineraryDetailDto>> addDetail(
            @PathVariable Long id,
            @RequestBody ItineraryDetailDto detailDto,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to add detail to itinerary id: {} by: {}", id, email);
        ItineraryDetailDto created = itineraryService.addDetail(id, detailDto, email);
        return ResponseEntity.ok(ApiResponse.success("Thêm điểm dừng vào lịch trình thành công!", created));
    }

    @PutMapping("/details/{detailId}")
    public ResponseEntity<ApiResponse<ItineraryDetailDto>> updateDetail(
            @PathVariable Long detailId,
            @RequestBody ItineraryDetailDto detailDto,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to update detail id: {} by: {}", detailId, email);
        ItineraryDetailDto updated = itineraryService.updateDetail(detailId, detailDto, email);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật điểm dừng thành công!", updated));
    }

    @DeleteMapping("/details/{detailId}")
    public ResponseEntity<ApiResponse<Void>> deleteDetail(
            @PathVariable Long detailId,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to delete detail id: {} by: {}", detailId, email);
        itineraryService.deleteDetail(detailId, email);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa điểm dừng khỏi lịch trình", null));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<ItineraryMemberDto>> addMember(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String requesterEmail = requireAuthEmail(authentication);
        String memberEmail = request.get("email");
        String role = request.getOrDefault("role", "VIEWER");
        log.info("REST request to invite member {} with role {} to itinerary {} by {}", memberEmail, role, id, requesterEmail);
        ItineraryMemberDto member = itineraryService.addMember(id, memberEmail, role, requesterEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm thành viên vào chuyến đi!", member));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            Authentication authentication) {
        String requesterEmail = requireAuthEmail(authentication);
        log.info("REST request to remove member userId {} from itinerary {} by {}", userId, id, requesterEmail);
        itineraryService.removeMember(id, userId, requesterEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa thành viên khỏi chuyến đi", null));
    }

    @PostMapping("/{id}/expenses")
    public ResponseEntity<ApiResponse<ItineraryExpenseDto>> addExpense(
            @PathVariable Long id,
            @RequestBody ItineraryExpenseDto expenseDto,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to add expense to itinerary id: {} by {}", id, email);
        ItineraryExpenseDto expense = itineraryService.addExpense(id, expenseDto, email);
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận khoản chi tiêu!", expense));
    }

    @DeleteMapping("/expenses/{expenseId}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @PathVariable Long expenseId,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to delete expense id: {} by {}", expenseId, email);
        itineraryService.deleteExpense(expenseId, email);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa khoản chi tiêu", null));
    }

    @GetMapping("/{id}/budget-summary")
    public ResponseEntity<ApiResponse<BudgetSummaryDto>> getBudgetSummary(@PathVariable Long id) {
        log.info("REST request to get budget summary for itinerary id: {}", id);
        BudgetSummaryDto summary = itineraryService.getBudgetSummary(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy tổng hợp ngân sách thành công", summary));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<ApiResponse<ItineraryDto>> cloneItinerary(
            @PathVariable Long id,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to clone itinerary id: {} by: {}", id, email);
        ItineraryDto cloned = itineraryService.cloneItinerary(id, email);
        return ResponseEntity.ok(ApiResponse.success("Sao chép lịch trình thành công!", cloned));
    }

    @PostMapping("/ai-quick-generate")
    public ResponseEntity<ApiResponse<ItineraryDto>> aiQuickGenerate(
            @RequestBody AiTripGenerateRequest request,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        log.info("REST request to AI quick generate itinerary for: {}", email);
        ItineraryDto generated = itineraryService.aiQuickGenerate(request, email);
        return ResponseEntity.ok(ApiResponse.success("Đã khởi tạo lộ trình AI thông minh!", generated));
    }
}
