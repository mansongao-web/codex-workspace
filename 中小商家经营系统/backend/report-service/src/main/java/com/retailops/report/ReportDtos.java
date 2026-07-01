package com.retailops.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record DashboardResponse(
            Metric today,
            Metric week,
            Metric year,
            Integer openAlerts,
            Integer lowStockCount,
            List<TopProductResponse> topProducts
    ) {
    }

    public record Metric(
            BigDecimal revenue,
            BigDecimal profit,
            Integer orderCount,
            BigDecimal averageOrderValue,
            BigDecimal grossMarginRate
    ) {
    }

    public record SalesSummaryResponse(
            String period,
            LocalDate from,
            LocalDate to,
            List<SalesBucket> buckets,
            Metric total
    ) {
    }

    public record SalesBucket(
            String label,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal profit,
            Integer orderCount
    ) {
    }

    public record TopProductResponse(
            String productSku,
            String productName,
            Integer soldQuantity,
            BigDecimal revenue,
            BigDecimal profit
    ) {
    }

    public record SlowProductResponse(
            String productSku,
            String productName,
            Integer soldQuantity,
            Integer stockQuantity,
            LocalDate lastSoldDate
    ) {
    }

    public record ProfitDistributionResponse(
            String bucket,
            Integer lineCount,
            BigDecimal revenue,
            BigDecimal profit
    ) {
    }
}
