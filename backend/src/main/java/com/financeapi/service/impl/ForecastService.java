package com.financeapi.service.impl;

import com.financeapi.dto.response.ForecastResponse;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.CategoryRepository;
import com.financeapi.repository.TransactionRepository;
import com.financeapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final double ALPHA = 0.3;

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','ADMIN')")
    public ForecastResponse forecast(String userEmail, int days) {
        Long userId = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail))
                .getId();

        // Fetch last 30 days of daily expense data grouped by category
        List<Object[]> rows = transactionRepository.dailyExpenseByCategory(
                userId, LocalDate.now().minusDays(30));

        // categoryId -> ordered list of daily amounts
        Map<Long, List<BigDecimal>> dailyData = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long catId = ((Number) row[0]).longValue();
            BigDecimal amount = new BigDecimal(row[2].toString());
            dailyData.computeIfAbsent(catId, k -> new ArrayList<>()).add(amount);
        }

        Map<String, BigDecimal> dailyForecast = new LinkedHashMap<>();
        Map<String, BigDecimal> projectedTotal = new LinkedHashMap<>();

        for (Map.Entry<Long, List<BigDecimal>> entry : dailyData.entrySet()) {
            String catName = categoryRepository.findById(entry.getKey())
                    .map(c -> c.getName()).orElse("Unknown");

            List<BigDecimal> actuals = entry.getValue();
            // Seed forecast with first actual
            double smoothed = actuals.get(0).doubleValue();
            for (int i = 1; i < actuals.size(); i++) {
                smoothed = ALPHA * actuals.get(i).doubleValue() + (1 - ALPHA) * smoothed;
            }

            BigDecimal daily = BigDecimal.valueOf(smoothed).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = daily.multiply(BigDecimal.valueOf(days));
            dailyForecast.put(catName, daily);
            projectedTotal.put(catName, total);
        }

        return new ForecastResponse(days, projectedTotal, dailyForecast);
    }
}
