package com.financeapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class ForecastResponse {
    private int forecastDays;
    // category name -> projected total spend over forecastDays
    private Map<String, BigDecimal> projectedSpendByCategory;
    // category name -> daily forecast value (last smoothed value)
    private Map<String, BigDecimal> dailyForecastByCategory;
}
