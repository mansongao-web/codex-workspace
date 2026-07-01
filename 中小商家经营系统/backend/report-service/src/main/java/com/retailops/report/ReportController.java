package com.retailops.report;

import com.retailops.report.ReportDtos.DashboardResponse;
import com.retailops.report.ReportDtos.ProfitDistributionResponse;
import com.retailops.report.ReportDtos.SalesSummaryResponse;
import com.retailops.report.ReportDtos.SlowProductResponse;
import com.retailops.report.ReportDtos.TopProductResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }

    @GetMapping("/sales-summary")
    public SalesSummaryResponse salesSummary(
            @RequestParam(name = "period", defaultValue = "DAY") String period,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.salesSummary(period, from, to);
    }

    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "orderBy", defaultValue = "quantity") String orderBy
    ) {
        return service.topProducts(from, to, limit, orderBy);
    }

    @GetMapping("/slow-products")
    public List<SlowProductResponse> slowProducts(
            @RequestParam(name = "days", defaultValue = "30") int days,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return service.slowProducts(days, limit);
    }

    @GetMapping("/profit-distribution")
    public List<ProfitDistributionResponse> profitDistribution(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.profitDistribution(from, to);
    }
}
