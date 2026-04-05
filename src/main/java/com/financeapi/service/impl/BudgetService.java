package com.financeapi.service.impl;

import com.financeapi.domain.Budget;
import com.financeapi.domain.Category;
import com.financeapi.domain.User;
import com.financeapi.dto.request.BudgetRequest;
import com.financeapi.dto.response.BudgetStatusResponse;
import com.financeapi.dto.response.BudgetStatusResponse.EnvelopeStatus;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.BudgetRepository;
import com.financeapi.repository.CategoryRepository;
import com.financeapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Budget createBudget(BudgetRequest request, String userEmail) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        Budget budget = budgetRepository
                .findByCategoryIdAndMonthYear(request.getCategoryId(), request.getMonthYear())
                .orElse(new Budget());
        budget.setCategory(category);
        budget.setMonthYear(request.getMonthYear());
        budget.setAmountLimit(request.getAmountLimit());
        budget.setCreatedBy(user);
        return budgetRepository.save(budget);
    }

    public BudgetStatusResponse getStatus(String monthYear) {
        List<Budget> budgets = budgetRepository.findByMonthYear(monthYear);
        YearMonth ym = YearMonth.parse(monthYear);
        int totalDays = ym.lengthOfMonth();
        int dayOfMonth = LocalDate.now().getMonthValue() == ym.getMonthValue()
                && LocalDate.now().getYear() == ym.getYear()
                ? LocalDate.now().getDayOfMonth() : totalDays;
        int daysRemaining = Math.max(0, totalDays - dayOfMonth);

        List<EnvelopeStatus> envelopes = budgets.stream().map(b -> {
            BigDecimal spent = budgetRepository.sumSpendByCategoryAndMonth(
                    b.getCategory().getId(), monthYear);
            double pct = b.getAmountLimit().compareTo(BigDecimal.ZERO) == 0 ? 0
                    : spent.divide(b.getAmountLimit(), 4, RoundingMode.HALF_UP)
                           .multiply(BigDecimal.valueOf(100)).doubleValue();

            // Project end-of-month spend based on daily burn rate
            BigDecimal projected = null;
            if (dayOfMonth > 0) {
                BigDecimal dailyBurn = spent.divide(BigDecimal.valueOf(dayOfMonth), 4, RoundingMode.HALF_UP);
                BigDecimal projectedTotal = dailyBurn.multiply(BigDecimal.valueOf(totalDays));
                if (projectedTotal.compareTo(b.getAmountLimit()) > 0) {
                    projected = projectedTotal.subtract(b.getAmountLimit()).setScale(2, RoundingMode.HALF_UP);
                }
            }

            return new EnvelopeStatus(b.getId(), b.getCategory().getName(),
                    b.getAmountLimit(), spent, pct, daysRemaining, projected, pct >= 80.0);
        }).collect(Collectors.toList());

        return new BudgetStatusResponse(monthYear, envelopes);
    }
}
