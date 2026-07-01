package com.retailops.report;

import com.retailops.report.ReportDtos.DashboardResponse;
import com.retailops.report.ReportDtos.Metric;
import com.retailops.report.ReportDtos.ProfitDistributionResponse;
import com.retailops.report.ReportDtos.SalesBucket;
import com.retailops.report.ReportDtos.SalesSummaryResponse;
import com.retailops.report.ReportDtos.SlowProductResponse;
import com.retailops.report.ReportDtos.TopProductResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardResponse dashboard() {
        Metric today = metric("""
                WHERE status = 'PAID' AND business_date = CURRENT_DATE
                """);
        Metric week = metric("""
                WHERE status = 'PAID' AND YEARWEEK(business_date, 1) = YEARWEEK(CURRENT_DATE, 1)
                """);
        Metric year = metric("""
                WHERE status = 'PAID' AND YEAR(business_date) = YEAR(CURRENT_DATE)
                """);
        Integer openAlerts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_alerts WHERE resolved = FALSE",
                Integer.class
        );
        Integer lowStockCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_levels WHERE quantity <= min_quantity",
                Integer.class
        );
        return new DashboardResponse(today, week, year, openAlerts, lowStockCount, topProducts(null, null, 5, "revenue"));
    }

    public SalesSummaryResponse salesSummary(String period, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        String normalized = period == null ? "DAY" : period.trim().toUpperCase();
        String labelExpression = switch (normalized) {
            case "WEEK" -> "CONCAT(YEAR(business_date), '-W', LPAD(WEEK(business_date, 1), 2, '0'))";
            case "MONTH" -> "DATE_FORMAT(business_date, '%Y-%m')";
            case "YEAR" -> "DATE_FORMAT(business_date, '%Y')";
            default -> "DATE_FORMAT(business_date, '%Y-%m-%d')";
        };
        List<SalesBucket> buckets = jdbc.query("""
                SELECT %s AS bucket_label,
                       COALESCE(SUM(payable_amount), 0) AS revenue,
                       COALESCE(SUM(cost_amount), 0) AS cost,
                       COALESCE(SUM(profit_amount), 0) AS profit,
                       COUNT(*) AS order_count
                FROM sales_orders
                WHERE status = 'PAID' AND business_date BETWEEN ? AND ?
                GROUP BY bucket_label
                ORDER BY bucket_label
                """.formatted(labelExpression),
                (rs, rowNum) -> new SalesBucket(
                        rs.getString("bucket_label"),
                        rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("cost"),
                        rs.getBigDecimal("profit"),
                        rs.getInt("order_count")
                ),
                start,
                end
        );
        Metric total = metric("""
                WHERE status = 'PAID' AND business_date BETWEEN '%s' AND '%s'
                """.formatted(start, end));
        return new SalesSummaryResponse(normalized, start, end, buckets, total);
    }

    public List<TopProductResponse> topProducts(LocalDate from, LocalDate to, int limit, String orderBy) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sortColumn = switch (orderBy == null ? "quantity" : orderBy) {
            case "profit" -> "profit";
            case "revenue" -> "revenue";
            default -> "sold_quantity";
        };
        return jdbc.query("""
                SELECT soi.product_sku,
                       soi.product_name,
                       SUM(soi.quantity) AS sold_quantity,
                       COALESCE(SUM(soi.line_amount), 0) AS revenue,
                       COALESCE(SUM(soi.profit_amount), 0) AS profit
                FROM sales_order_items soi
                JOIN sales_orders so ON so.id = soi.order_id
                WHERE so.status = 'PAID' AND so.business_date BETWEEN ? AND ?
                GROUP BY soi.product_sku, soi.product_name
                ORDER BY %s DESC
                LIMIT ?
                """.formatted(sortColumn),
                (rs, rowNum) -> new TopProductResponse(
                        rs.getString("product_sku"),
                        rs.getString("product_name"),
                        rs.getInt("sold_quantity"),
                        rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("profit")
                ),
                start,
                end,
                safeLimit
        );
    }

    public List<SlowProductResponse> slowProducts(int days, int limit) {
        int safeDays = Math.max(7, Math.min(days, 365));
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
                SELECT p.sku AS product_sku,
                       p.name AS product_name,
                       COALESCE(SUM(CASE WHEN so.business_date >= DATE_SUB(CURRENT_DATE, INTERVAL %d DAY)
                                         THEN soi.quantity ELSE 0 END), 0) AS sold_quantity,
                       COALESCE(sl.quantity, 0) AS stock_quantity,
                       MAX(so.business_date) AS last_sold_date
                FROM products p
                LEFT JOIN stock_levels sl ON sl.product_sku = p.sku
                LEFT JOIN sales_order_items soi ON soi.product_sku = p.sku
                LEFT JOIN sales_orders so ON so.id = soi.order_id AND so.status = 'PAID'
                WHERE p.status = 'ACTIVE'
                GROUP BY p.sku, p.name, sl.quantity
                HAVING sold_quantity <= 2
                ORDER BY sold_quantity ASC, stock_quantity DESC, p.name
                LIMIT ?
                """.formatted(safeDays),
                (rs, rowNum) -> new SlowProductResponse(
                        rs.getString("product_sku"),
                        rs.getString("product_name"),
                        rs.getInt("sold_quantity"),
                        rs.getInt("stock_quantity"),
                        toLocalDate(rs.getDate("last_sold_date"))
                ),
                safeLimit
        );
    }

    public List<ProfitDistributionResponse> profitDistribution(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return jdbc.query("""
                SELECT margin_bucket,
                       COUNT(*) AS line_count,
                       COALESCE(SUM(line_amount), 0) AS revenue,
                       COALESCE(SUM(profit_amount), 0) AS profit
                FROM (
                    SELECT line_amount,
                           profit_amount,
                           CASE
                               WHEN unit_price = 0 THEN 'unknown'
                               WHEN (unit_price - unit_cost) / unit_price < 0 THEN 'negative'
                               WHEN (unit_price - unit_cost) / unit_price < 0.1 THEN '0-10%'
                               WHEN (unit_price - unit_cost) / unit_price < 0.2 THEN '10-20%'
                               WHEN (unit_price - unit_cost) / unit_price < 0.3 THEN '20-30%'
                               WHEN (unit_price - unit_cost) / unit_price < 0.5 THEN '30-50%'
                               ELSE '50%+'
                           END AS margin_bucket
                    FROM sales_order_items soi
                    JOIN sales_orders so ON so.id = soi.order_id
                    WHERE so.status = 'PAID' AND so.business_date BETWEEN ? AND ?
                ) t
                GROUP BY margin_bucket
                ORDER BY FIELD(margin_bucket, 'negative', '0-10%', '10-20%', '20-30%', '30-50%', '50%+', 'unknown')
                """,
                (rs, rowNum) -> new ProfitDistributionResponse(
                        rs.getString("margin_bucket"),
                        rs.getInt("line_count"),
                        rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("profit")
                ),
                start,
                end
        );
    }

    private Metric metric(String whereClause) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(payable_amount), 0) AS revenue,
                       COALESCE(SUM(profit_amount), 0) AS profit,
                       COUNT(*) AS order_count,
                       COALESCE(AVG(payable_amount), 0) AS average_order_value
                FROM sales_orders
                %s
                """.formatted(whereClause),
                (rs, rowNum) -> {
                    BigDecimal revenue = rs.getBigDecimal("revenue");
                    BigDecimal profit = rs.getBigDecimal("profit");
                    return new Metric(
                            revenue,
                            profit,
                            rs.getInt("order_count"),
                            rs.getBigDecimal("average_order_value"),
                            marginRate(revenue, profit)
                    );
                }
        );
    }

    private BigDecimal marginRate(BigDecimal revenue, BigDecimal profit) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return profit.divide(revenue, 4, RoundingMode.HALF_UP);
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
