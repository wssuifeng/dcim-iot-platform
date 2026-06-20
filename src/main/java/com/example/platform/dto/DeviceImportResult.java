package com.example.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备批量导入结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportResult {

    private int totalCount;
    private int successCount;
    private int failCount;
    private List<FailDetail> failDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailDetail {
        private int rowIndex;
        private String deviceCode;
        private String reason;
    }
}
