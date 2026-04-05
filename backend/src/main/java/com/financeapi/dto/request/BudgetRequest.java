package com.financeapi.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetRequest {
    @NotNull
    private Long categoryId;

    @NotBlank
    private String monthYear; // format: YYYY-MM

    @NotNull @DecimalMin("0.01")
    private BigDecimal amountLimit;
}
