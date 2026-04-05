package com.financeapi.service.impl;

import com.financeapi.dto.response.DashboardSummaryResponse;
import com.financeapi.dto.response.TransactionResponse;
import com.financeapi.repository.TransactionRepository;
import com.financeapi.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final TransactionRepository transactionRepository;

    @Override
    @Cacheable("dashboard-summary")
    public DashboardSummaryResponse getSummary() {
        BigDecimal income = transactionRepository.sumIncome();
        BigDecimal expenses = transactionRepository.sumExpenses();
        if (income == null) income = BigDecimal.ZERO;
        if (expenses == null) expenses = BigDecimal.ZERO;

        Map<String, BigDecimal> categoryBreakdown = transactionRepository.sumByCategory().stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? row[0].toString() : "Unknown",
                        row -> row[1] instanceof BigDecimal bd ? bd : new BigDecimal(row[1].toString()),
                        (a, b) -> a, LinkedHashMap::new));

        List<TransactionResponse> recent = transactionRepository
                .findByDeletedFalse(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(TransactionResponse::from)
                .getContent();

        return new DashboardSummaryResponse(income, expenses, income.subtract(expenses), recent, categoryBreakdown);
    }

    @Override
    @Cacheable("monthly-trends")
    public Map<String, Object> getMonthlyTrends() {
        List<Object[]> rows = transactionRepository.monthlyTrends();
        Map<String, Map<String, BigDecimal>> trends = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String month = row[0] != null ? row[0].toString() : "Unknown";
            BigDecimal amount = row[1] instanceof BigDecimal bd ? bd
                    : new BigDecimal(row[1].toString());
            String type = row[2] != null ? row[2].toString() : "UNKNOWN";
            trends.computeIfAbsent(month, k -> new LinkedHashMap<>()).put(type, amount);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trends", trends);
        return result;
    }
}
