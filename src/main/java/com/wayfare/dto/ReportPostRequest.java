package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportPostRequest {
    @NotBlank(message = "Vui lòng chọn hoặc nhập lý do báo cáo vi phạm")
    private String reason;

    private String category; // e.g. "Spam / Quảng cáo", "Đòi nợ / Tranh chấp", "Lừa đảo / Sai lệch", "Nội dung phản cảm", "Khác"
    private String details;
}
