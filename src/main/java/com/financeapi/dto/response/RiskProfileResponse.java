package com.financeapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RiskProfileResponse {
    private Long userId;
    private String email;
    private double velocityScore;
    private BigDecimal spendLast24h;
    private BigDecimal spendLast7d;
    private String riskLevel; // LOW, MEDIUM, HIGH
}
