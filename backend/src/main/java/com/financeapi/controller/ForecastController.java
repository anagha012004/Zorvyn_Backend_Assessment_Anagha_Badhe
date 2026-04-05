package com.financeapi.controller;

import com.financeapi.dto.response.ForecastResponse;
import com.financeapi.service.impl.ForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/forecast")
@RequiredArgsConstructor
@Tag(name = "Spending Forecast")
public class ForecastController {

    private final ForecastService forecastService;

    @GetMapping
    @Operation(summary = "Projected daily spend per category using exponential smoothing (α=0.3)")
    public ResponseEntity<ForecastResponse> getForecast(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(forecastService.forecast(user.getUsername(), days));
    }
}
