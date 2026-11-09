package com.wayfare.modules.admin.service;

import com.wayfare.modules.admin.dto.SystemMonitoringDto;
import com.wayfare.modules.admin.dto.SystemMonitoringDto.*;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemMonitoringService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    /**
     * Thu thập toàn diện dữ liệu giám sát hệ thống và cơ sở dữ liệu.
     */
    public SystemMonitoringDto getSystemMonitoringData() {
        log.info("Bắt đầu thu thập dữ liệu giám sát hệ thống (System & Database Monitoring)");

        ServerInfoDto serverInfo = collectServerInfo();
        JvmMemoryDto memoryInfo = collectMemoryInfo();
        JvmThreadsDto threadInfo = collectThreadInfo();
        List<GarbageCollectorInfoDto> gcInfo = collectGarbageCollectorInfo();
        List<DatabaseTableInfoDto> tables = collectDatabaseTables();
        DatabaseSystemInfoDto databaseInfo = collectDatabaseInfo(tables);

        return SystemMonitoringDto.builder()
                .serverInfo(serverInfo)
                .memoryInfo(memoryInfo)
                .threadInfo(threadInfo)
                .garbageCollectors(gcInfo)
                .databaseInfo(databaseInfo)
                .tables(tables)
                .build();
    }

    /**
     * Chi tiết các cột (schema) của một bảng dữ liệu cụ thể.
     */
    public DatabaseTableDetailDto getTableDetails(String tableName) {
        log.info("Thu thập cấu trúc chi tiết bảng CSDL: {}", tableName);
        List<DatabaseColumnInfoDto> columns = new ArrayList<>();
        String engine = "InnoDB";
        String collation = "utf8mb4_unicode_ci";
        String comment = "";
        Long rowCount = 0L;

        try (Connection conn = dataSource.getConnection()) {
            String currentSchema = conn.getCatalog();
            if (currentSchema == null || currentSchema.isBlank()) {
                currentSchema = conn.getSchema();
            }

            // Lấy thông tin cơ bản của bảng
            String tableSql = """
                SELECT TABLE_ROWS, ENGINE, TABLE_COLLATION, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = ?) AND TABLE_NAME = ?
                LIMIT 1
            """;
            List<Map<String, Object>> tableRows = jdbcTemplate.queryForList(tableSql, currentSchema, tableName);
            if (!tableRows.isEmpty()) {
                Map<String, Object> r = tableRows.get(0);
                rowCount = r.get("TABLE_ROWS") != null ? ((Number) r.get("TABLE_ROWS")).longValue() : 0L;
                engine = r.get("ENGINE") != null ? r.get("ENGINE").toString() : "InnoDB";
                collation = r.get("TABLE_COLLATION") != null ? r.get("TABLE_COLLATION").toString() : "";
                comment = r.get("TABLE_COMMENT") != null ? r.get("TABLE_COMMENT").toString() : "";
            }

            // Lấy danh sách cột
            String columnSql = """
                SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = ?) AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION ASC
            """;
            List<Map<String, Object>> colRows = jdbcTemplate.queryForList(columnSql, currentSchema, tableName);

            for (Map<String, Object> col : colRows) {
                columns.add(DatabaseColumnInfoDto.builder()
                        .columnName(col.get("COLUMN_NAME") != null ? col.get("COLUMN_NAME").toString() : "")
                        .dataType(col.get("DATA_TYPE") != null ? col.get("DATA_TYPE").toString() : "")
                        .columnType(col.get("COLUMN_TYPE") != null ? col.get("COLUMN_TYPE").toString() : "")
                        .isNullable("YES".equalsIgnoreCase(col.get("IS_NULLABLE") != null ? col.get("IS_NULLABLE").toString() : ""))
                        .columnKey(col.get("COLUMN_KEY") != null ? col.get("COLUMN_KEY").toString() : "")
                        .columnDefault(col.get("COLUMN_DEFAULT") != null ? col.get("COLUMN_DEFAULT").toString() : null)
                        .extra(col.get("EXTRA") != null ? col.get("EXTRA").toString() : "")
                        .comment(col.get("COLUMN_COMMENT") != null ? col.get("COLUMN_COMMENT").toString() : "")
                        .build());
            }

        } catch (Exception e) {
            log.error("Lỗi khi truy vấn cấu trúc bảng {}: {}", tableName, e.getMessage(), e);
        }

        return DatabaseTableDetailDto.builder()
                .tableName(tableName)
                .rowCount(rowCount)
                .engine(engine)
                .collation(collation)
                .comment(comment)
                .columns(columns)
                .build();
    }

    private ServerInfoDto collectServerInfo() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        long uptime = runtimeMXBean.getUptime();
        Instant startTime = Instant.ofEpochMilli(runtimeMXBean.getStartTime());
        Instant now = Instant.now();

        String appName = environment.getProperty("spring.application.name", "wayfare-backend");
        String port = environment.getProperty("server.port", "8081");
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }

        return ServerInfoDto.builder()
                .appName(appName)
                .javaVersion(System.getProperty("java.version"))
                .javaVendor(System.getProperty("java.vendor"))
                .javaRuntime(System.getProperty("java.runtime.name"))
                .javaVmName(System.getProperty("java.vm.name"))
                .javaVmVersion(System.getProperty("java.vm.version"))
                .javaHome(System.getProperty("java.home"))
                .springBootVersion(SpringBootVersion.getVersion())
                .springFrameworkVersion(SpringVersion.getVersion())
                .hibernateVersion(org.hibernate.Version.getVersionString())
                .osName(System.getProperty("os.name"))
                .osVersion(System.getProperty("os.version"))
                .osArch(System.getProperty("os.arch"))
                .availableProcessors(Runtime.getRuntime().availableProcessors())
                .systemLoadAverage(osBean.getSystemLoadAverage())
                .uptimeMillis(uptime)
                .formattedUptime(formatDuration(uptime))
                .startTime(ISO_FORMATTER.format(startTime))
                .currentTime(ISO_FORMATTER.format(now))
                .timeZone(ZoneId.systemDefault().getId())
                .serverPort(port)
                .activeProfiles(Arrays.asList(profiles))
                .build();
    }

    private JvmMemoryDto collectMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;

        double usagePercent = 0.0;
        if (max > 0) {
            usagePercent = Math.round(((double) used / max) * 1000.0) / 10.0;
        }

        return JvmMemoryDto.builder()
                .maxMemoryBytes(max)
                .totalMemoryBytes(total)
                .freeMemoryBytes(free)
                .usedMemoryBytes(used)
                .maxMemoryFormatted(formatBytes(max))
                .totalMemoryFormatted(formatBytes(total))
                .freeMemoryFormatted(formatBytes(free))
                .usedMemoryFormatted(formatBytes(used))
                .usagePercent(usagePercent)
                .build();
    }

    private JvmThreadsDto collectThreadInfo() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        return JvmThreadsDto.builder()
                .threadCount(threadBean.getThreadCount())
                .peakThreadCount(threadBean.getPeakThreadCount())
                .daemonThreadCount(threadBean.getDaemonThreadCount())
                .totalStartedThreadCount(threadBean.getTotalStartedThreadCount())
                .build();
    }

    private List<GarbageCollectorInfoDto> collectGarbageCollectorInfo() {
        List<GarbageCollectorInfoDto> list = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            list.add(GarbageCollectorInfoDto.builder()
                    .name(gc.getName())
                    .collectionCount(gc.getCollectionCount())
                    .collectionTimeMillis(gc.getCollectionTime())
                    .build());
        }
        return list;
    }

    private List<DatabaseTableInfoDto> collectDatabaseTables() {
        List<DatabaseTableInfoDto> tables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            String currentSchema = conn.getCatalog();
            if (currentSchema == null || currentSchema.isBlank()) {
                currentSchema = conn.getSchema();
            }

            // Đếm số lượng cột theo bảng
            Map<String, Integer> columnCountMap = new HashMap<>();
            try {
                String colCountSql = """
                    SELECT TABLE_NAME, COUNT(*) AS col_count
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = ?
                    GROUP BY TABLE_NAME
                """;
                List<Map<String, Object>> colRows = jdbcTemplate.queryForList(colCountSql, currentSchema);
                for (Map<String, Object> row : colRows) {
                    String tName = (String) row.get("TABLE_NAME");
                    Number count = (Number) row.get("col_count");
                    if (tName != null && count != null) {
                        columnCountMap.put(tName.toLowerCase(), count.intValue());
                    }
                }
            } catch (Exception ex) {
                log.warn("Không thể lấy số lượng cột từ information_schema.COLUMNS: {}", ex.getMessage());
            }

            // Truy vấn danh sách bảng
            String sql = """
                SELECT 
                    TABLE_NAME, 
                    TABLE_ROWS, 
                    DATA_LENGTH, 
                    INDEX_LENGTH, 
                    ENGINE, 
                    TABLE_COLLATION, 
                    CREATE_TIME, 
                    UPDATE_TIME, 
                    TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE (TABLE_SCHEMA = DATABASE() OR TABLE_SCHEMA = ?)
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME ASC
            """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, currentSchema);

            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("TABLE_NAME");
                long rowCount = row.get("TABLE_ROWS") != null ? ((Number) row.get("TABLE_ROWS")).longValue() : 0L;
                long dataLen = row.get("DATA_LENGTH") != null ? ((Number) row.get("DATA_LENGTH")).longValue() : 0L;
                long indexLen = row.get("INDEX_LENGTH") != null ? ((Number) row.get("INDEX_LENGTH")).longValue() : 0L;
                long totalSize = dataLen + indexLen;

                String engine = row.get("ENGINE") != null ? row.get("ENGINE").toString() : "InnoDB";
                String collation = row.get("TABLE_COLLATION") != null ? row.get("TABLE_COLLATION").toString() : "";
                String createTime = row.get("CREATE_TIME") != null ? row.get("CREATE_TIME").toString() : null;
                String updateTime = row.get("UPDATE_TIME") != null ? row.get("UPDATE_TIME").toString() : null;
                String comment = row.get("TABLE_COMMENT") != null ? row.get("TABLE_COMMENT").toString() : "";
                int colCount = columnCountMap.getOrDefault(tableName.toLowerCase(), 0);

                tables.add(DatabaseTableInfoDto.builder()
                        .tableName(tableName)
                        .rowCount(rowCount)
                        .dataSizeBytes(dataLen)
                        .dataSizeFormatted(formatBytes(dataLen))
                        .indexSizeBytes(indexLen)
                        .indexSizeFormatted(formatBytes(indexLen))
                        .totalSizeBytes(totalSize)
                        .totalSizeFormatted(formatBytes(totalSize))
                        .engine(engine)
                        .collation(collation)
                        .createTime(createTime)
                        .updateTime(updateTime)
                        .comment(comment)
                        .columnCount(colCount)
                        .build());
            }

        } catch (Exception e) {
            log.error("Lỗi khi truy vấn danh sách bảng CSDL: {}", e.getMessage(), e);
        }

        return tables;
    }

    private DatabaseSystemInfoDto collectDatabaseInfo(List<DatabaseTableInfoDto> tables) {
        String prodName = "MySQL";
        String prodVer = "8.x";
        String driverName = "";
        String driverVer = "";
        String dbName = "wayfare_db";
        String rawUrl = "";

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            prodName = meta.getDatabaseProductName();
            prodVer = meta.getDatabaseProductVersion();
            driverName = meta.getDriverName();
            driverVer = meta.getDriverVersion();
            rawUrl = meta.getURL();
            dbName = conn.getCatalog();
            if (dbName == null || dbName.isBlank()) {
                dbName = conn.getSchema();
            }
        } catch (Exception e) {
            log.error("Không thể đọc metadata cơ sở dữ liệu: {}", e.getMessage());
        }

        long totalRows = 0;
        long totalDataBytes = 0;
        long totalIndexBytes = 0;

        for (DatabaseTableInfoDto t : tables) {
            totalRows += (t.getRowCount() != null ? t.getRowCount() : 0);
            totalDataBytes += (t.getDataSizeBytes() != null ? t.getDataSizeBytes() : 0);
            totalIndexBytes += (t.getIndexSizeBytes() != null ? t.getIndexSizeBytes() : 0);
        }

        long totalBytes = totalDataBytes + totalIndexBytes;

        // HikariCP Connection Pool
        ConnectionPoolInfoDto poolInfo = null;
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean poolMxBean = hikari.getHikariPoolMXBean();
            if (poolMxBean != null) {
                poolInfo = ConnectionPoolInfoDto.builder()
                        .poolName(hikari.getPoolName())
                        .activeConnections(poolMxBean.getActiveConnections())
                        .idleConnections(poolMxBean.getIdleConnections())
                        .totalConnections(poolMxBean.getTotalConnections())
                        .maxPoolSize(hikari.getMaximumPoolSize())
                        .minIdle(hikari.getMinimumIdle())
                        .threadsAwaitingConnection(poolMxBean.getThreadsAwaitingConnection())
                        .build();
            } else {
                poolInfo = ConnectionPoolInfoDto.builder()
                        .poolName(hikari.getPoolName())
                        .maxPoolSize(hikari.getMaximumPoolSize())
                        .minIdle(hikari.getMinimumIdle())
                        .build();
            }
        }

        return DatabaseSystemInfoDto.builder()
                .productName(prodName)
                .productVersion(prodVer)
                .driverName(driverName)
                .driverVersion(driverVer)
                .databaseName(dbName)
                .sanitizedUrl(maskDatabaseUrl(rawUrl))
                .totalTables(tables.size())
                .totalRows(totalRows)
                .totalDataSizeBytes(totalDataBytes)
                .totalDataSizeFormatted(formatBytes(totalDataBytes))
                .totalIndexSizeBytes(totalIndexBytes)
                .totalIndexSizeFormatted(formatBytes(totalIndexBytes))
                .totalSizeBytes(totalBytes)
                .totalSizeFormatted(formatBytes(totalBytes))
                .connectionPool(poolInfo)
                .build();
    }

    private String maskDatabaseUrl(String url) {
        if (url == null || url.isBlank()) return "jdbc:mysql://localhost:3306/wayfare_db";
        // Ẩn mật khẩu nếu url có chứa user/password
        return url.replaceAll("password=[^&]*", "password=******")
                .replaceAll("pwd=[^&]*", "pwd=******");
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long days = seconds / (24 * 3600);
        seconds %= (24 * 3600);
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" ngày ");
        if (hours > 0 || days > 0) sb.append(hours).append(" giờ ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append(" phút ");
        sb.append(seconds).append(" giây");
        return sb.toString();
    }
}
