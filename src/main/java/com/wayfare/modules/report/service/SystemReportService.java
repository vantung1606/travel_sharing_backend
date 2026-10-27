package com.wayfare.modules.report.service;

import com.wayfare.modules.report.dto.CreateReportRequest;
import com.wayfare.modules.report.dto.ReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Deprecated
public class SystemReportService {

    private final ReportService reportService;

    public ReportDto submitReport(CreateReportRequest request, String reporterEmail) {
        return reportService.submitReport(request, reporterEmail);
    }

    public List<ReportDto> getReports(String status, String targetType) {
        return reportService.getReports(status, targetType);
    }

    public ReportDto resolveReport(Long reportId, String action, String notes, String adminEmail) {
        return reportService.resolveReport(reportId, action, notes, adminEmail);
    }
}
