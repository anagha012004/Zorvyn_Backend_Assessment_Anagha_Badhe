package com.financeapi.service;

import com.financeapi.domain.Transaction.TransactionType;
import com.financeapi.dto.request.TransactionRequest;
import com.financeapi.dto.response.AuditHistoryResponse;
import com.financeapi.dto.response.PagedResponse;
import com.financeapi.dto.response.TransactionResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface TransactionService {
    TransactionResponse create(TransactionRequest request, String idempotencyKey, String userEmail);
    TransactionResponse update(Long id, TransactionRequest request, String userEmail);
    void delete(Long id, String userEmail);
    TransactionResponse getById(Long id);
    PagedResponse<TransactionResponse> getAll(TransactionType type, Long categoryId,
                                               LocalDate from, LocalDate to, Pageable pageable);
    PagedResponse<TransactionResponse> getDeleted(Pageable pageable);
    TransactionResponse restore(Long id, String userEmail);
    byte[] exportCsv();
    byte[] exportExcel();
    AuditHistoryResponse getHistory(Long id);
}
