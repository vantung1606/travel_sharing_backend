package com.wayfare.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wrapper chuẩn cho các API trả dữ liệu phân trang.
 * Giữ nguyên dạng ApiResponse { success, message, data, timestamp } và đặt
 * PagedResponse này vào bên trong trường "data".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
}
