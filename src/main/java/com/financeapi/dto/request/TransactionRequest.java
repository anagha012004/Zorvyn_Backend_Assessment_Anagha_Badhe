package com.financeapi.dto.request;

import com.financeapi.domain.Transaction.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransactionRequest {
    @NotNull @DecimalMin("0.01")
    private BigDecimal amount;

    @NotNull
    private TransactionType type;

    private Long categoryId;

    @NotNull
    private LocalDate date;

    @Size(max = 1000)
    private String notes;
}
