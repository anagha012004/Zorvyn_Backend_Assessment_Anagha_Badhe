package com.financeapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class AuditHistoryResponse {
    private Long transactionId;
    private List<HistoryEntry> history;

    @Data
    @AllArgsConstructor
    public static class HistoryEntry {
        private String action;
        private String oldValue;
        private String newValue;
        private LocalDateTime timestamp;
    }
}
