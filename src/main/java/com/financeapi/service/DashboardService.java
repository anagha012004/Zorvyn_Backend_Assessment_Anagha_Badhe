package com.financeapi.service;

import com.financeapi.dto.response.DashboardSummaryResponse;

import java.util.List;
import java.util.Map;

public interface DashboardService {
    DashboardSummaryResponse getSummary();
    Map<String, Object> getMonthlyTrends();
}
