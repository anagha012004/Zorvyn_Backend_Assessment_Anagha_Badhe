package com.financeapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class BudgetStatusResponse {
    private String monthYear;
    private List<EnvelopeStatus> envelopes;

    @Data
    @AllArgsConstructor
    public static class EnvelopeStatus {
        private Long budgetId;
        private String categoryName;
        private BigDecimal limit;
        private BigDecimal spent;
        private double percentConsumed;
        private int daysRemaining;
        private BigDecimal projectedOverage; // null if no overage projected
        private boolean warningThresholdHit; // true when >= 80%
    }
}
