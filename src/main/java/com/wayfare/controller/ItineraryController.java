package com.wayfare.controller;

import com.wayfare.dto.*;
import com.wayfare.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
@Slf4j
public class ItineraryController {

    private final ItineraryService itineraryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ItineraryDto>>> getMyItineraries(
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to get itineraries for: {}", email);
        List<ItineraryDto> list = itineraryService.getItinerariesForUser(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch trình thành công", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ItineraryDto>> getItineraryById(
            @PathVariable Long id,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to get itinerary id: {} by: {}", id, email);
        ItineraryDto dto = itineraryService.getItineraryById(id, email);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết lịch trình thành công", dto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItineraryDto>> createItinerary(
            @RequestBody ItineraryDto request,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to create itinerary: '{}' for: {}", request.getTitle(), email);
        ItineraryDto created = itineraryService.createItinerary(request, email);
        return ResponseEntity.ok(ApiResponse.success("Tạo lịch trình chuyến đi thành công!", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ItineraryDto>> updateItinerary(
            @PathVariable Long id,
            @RequestBody ItineraryDto request,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to update itinerary id: {} by: {}", id, email);
        ItineraryDto updated = itineraryService.updateItinerary(id, request, email);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật lịch trình thành công!", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteItinerary(
            @PathVariable Long id,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to delete itinerary id: {} by: {}", id, email);
        itineraryService.deleteItinerary(id, email);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa lịch trình chuyến đi thành công", null));
    }

    @PostMapping("/{id}/details")
    public ResponseEntity<ApiResponse<ItineraryDetailDto>> addDetail(
            @PathVariable Long id,
            @RequestBody ItineraryDetailDto detailDto,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to add detail to itinerary id: {}", id);
        ItineraryDetailDto created = itineraryService.addDetail(id, detailDto, email);
        return ResponseEntity.ok(ApiResponse.success("Thêm điểm dừng vào lịch trình thành công!", created));
    }

    @PutMapping("/details/{detailId}")
    public ResponseEntity<ApiResponse<ItineraryDetailDto>> updateDetail(
            @PathVariable Long detailId,
            @RequestBody ItineraryDetailDto detailDto,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to update detail id: {}", detailId);
        ItineraryDetailDto updated = itineraryService.updateDetail(detailId, detailDto, email);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật điểm dừng thành công!", updated));
    }

    @DeleteMapping("/details/{detailId}")
    public ResponseEntity<ApiResponse<Void>> deleteDetail(
            @PathVariable Long detailId,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to delete detail id: {}", detailId);
        itineraryService.deleteDetail(detailId, email);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa điểm dừng khỏi lịch trình", null));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<ItineraryMemberDto>> addMember(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String requesterEmail) {
        String memberEmail = request.get("email");
        String role = request.getOrDefault("role", "VIEWER");
        log.info("REST request to invite member {} with role {} to itinerary {}", memberEmail, role, id);
        ItineraryMemberDto member = itineraryService.addMember(id, memberEmail, role, requesterEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm thành viên vào chuyến đi!", member));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String requesterEmail) {
        log.info("REST request to remove member userId {} from itinerary {}", userId, id);
        itineraryService.removeMember(id, userId, requesterEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa thành viên khỏi chuyến đi", null));
    }

    @PostMapping("/{id}/expenses")
    public ResponseEntity<ApiResponse<ItineraryExpenseDto>> addExpense(
            @PathVariable Long id,
            @RequestBody ItineraryExpenseDto expenseDto,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to add expense to itinerary id: {}", id);
        ItineraryExpenseDto expense = itineraryService.addExpense(id, expenseDto, email);
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận khoản chi tiêu!", expense));
    }

    @DeleteMapping("/expenses/{expenseId}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @PathVariable Long expenseId,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to delete expense id: {}", expenseId);
        itineraryService.deleteExpense(expenseId, email);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa khoản chi tiêu", null));
    }

    @GetMapping("/{id}/budget-summary")
    public ResponseEntity<ApiResponse<BudgetSummaryDto>> getBudgetSummary(@PathVariable Long id) {
        log.info("REST request to get budget summary for itinerary id: {}", id);
        BudgetSummaryDto summary = itineraryService.getBudgetSummary(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy tổng hợp ngân sách thành công", summary));
    }
}
