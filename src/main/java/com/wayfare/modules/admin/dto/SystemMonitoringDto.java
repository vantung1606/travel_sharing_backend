package com.wayfare.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMonitoringDto {

    private ServerInfoDto serverInfo;
    private JvmMemoryDto memoryInfo;
    private JvmThreadsDto threadInfo;
    private List<GarbageCollectorInfoDto> garbageCollectors;
    private DatabaseSystemInfoDto databaseInfo;
    private List<DatabaseTableInfoDto> tables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerInfoDto {
        private String appName;
        private String javaVersion;
        private String javaVendor;
        private String javaRuntime;
        private String javaVmName;
        private String javaVmVersion;
        private String javaHome;
        private String springBootVersion;
        private String springFrameworkVersion;
        private String hibernateVersion;
        private String osName;
        private String osVersion;
        private String osArch;
        private int availableProcessors;
        private double systemLoadAverage;
        private long uptimeMillis;
        private String formattedUptime;
        private String startTime;
        private String currentTime;
        private String timeZone;
        private String serverPort;
        private List<String> activeProfiles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JvmMemoryDto {
        private long maxMemoryBytes;
        private long totalMemoryBytes;
        private long freeMemoryBytes;
        private long usedMemoryBytes;
        private String maxMemoryFormatted;
        private String totalMemoryFormatted;
        private String freeMemoryFormatted;
        private String usedMemoryFormatted;
        private double usagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JvmThreadsDto {
        private int threadCount;
        private int peakThreadCount;
        private int daemonThreadCount;
        private long totalStartedThreadCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarbageCollectorInfoDto {
        private String name;
        private long collectionCount;
        private long collectionTimeMillis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseSystemInfoDto {
        private String productName;
        private String productVersion;
        private String driverName;
        private String driverVersion;
        private String databaseName;
        private String sanitizedUrl;
        private int totalTables;
        private long totalRows;
        private long totalDataSizeBytes;
        private String totalDataSizeFormatted;
        private long totalIndexSizeBytes;
        private String totalIndexSizeFormatted;
        private long totalSizeBytes;
        private String totalSizeFormatted;
        private ConnectionPoolInfoDto connectionPool;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionPoolInfoDto {
        private String poolName;
        private int activeConnections;
        private int idleConnections;
        private int totalConnections;
        private int maxPoolSize;
        private int minIdle;
        private int threadsAwaitingConnection;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseTableInfoDto {
        private String tableName;
        private Long rowCount;
        private Long dataSizeBytes;
        private String dataSizeFormatted;
        private Long indexSizeBytes;
        private String indexSizeFormatted;
        private Long totalSizeBytes;
        private String totalSizeFormatted;
        private String engine;
        private String collation;
        private String createTime;
        private String updateTime;
        private String comment;
        private Integer columnCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseTableDetailDto {
        private String tableName;
        private Long rowCount;
        private String engine;
        private String collation;
        private String comment;
        private List<DatabaseColumnInfoDto> columns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseColumnInfoDto {
        private String columnName;
        private String dataType;
        private String columnType;
        private Boolean isNullable;
        private String columnKey;
        private String columnDefault;
        private String extra;
        private String comment;
    }
}
