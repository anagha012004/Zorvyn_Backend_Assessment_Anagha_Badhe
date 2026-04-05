package com.financeapi.dto.response;

import com.financeapi.domain.Transaction;
import com.financeapi.domain.Transaction.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TransactionResponse {
    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private String categoryName;
    private LocalDate date;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
    private boolean offHours;
    private boolean possibleDuplicate;
    private String merchantName;

    public static TransactionResponse from(Transaction t) {
        TransactionResponse r = new TransactionResponse();
        r.id = t.getId();
        r.amount = t.getAmount();
        r.type = t.getType();
        r.date = t.getDate();
        r.notes = t.getNotes();
        r.createdAt = t.getCreatedAt();
        try { r.categoryName = t.getCategory() != null ? t.getCategory().getName() : null; }
        catch (Exception e) { r.categoryName = null; }
        try { r.createdBy = t.getCreatedBy() != null ? t.getCreatedBy().getFullName() : null; }
        catch (Exception e) { r.createdBy = null; }
        return r;
    }
}
