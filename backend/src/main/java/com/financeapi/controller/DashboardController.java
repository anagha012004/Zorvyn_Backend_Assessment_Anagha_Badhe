package com.financeapi.controller;

import com.financeapi.dto.response.DashboardSummaryResponse;
import com.financeapi.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Get total income, expenses, net balance, category breakdown, and recent transactions")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/trends")
    @Operation(summary = "Get month-by-month income/expense trends for the past year")
    public ResponseEntity<Map<String, Object>> getTrends() {
        return ResponseEntity.ok(dashboardService.getMonthlyTrends());
    }
}
