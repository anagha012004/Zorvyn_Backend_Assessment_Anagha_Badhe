package com.financeapi.controller;

import com.financeapi.dto.request.BudgetRequest;
import com.financeapi.dto.response.BudgetStatusResponse;
import com.financeapi.service.impl.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@Tag(name = "Budget Envelopes")
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    @Operation(summary = "Create or update a monthly budget envelope (ADMIN only)")
    public ResponseEntity<Void> createBudget(
            @Valid @RequestBody BudgetRequest request,
            @AuthenticationPrincipal UserDetails user) {
        budgetService.createBudget(request, user.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    @Operation(summary = "Real-time budget envelope status with projected overage")
    public ResponseEntity<BudgetStatusResponse> getStatus(
            @RequestParam(required = false) String monthYear) {
        if (monthYear == null) {
            monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return ResponseEntity.ok(budgetService.getStatus(monthYear));
    }
}
