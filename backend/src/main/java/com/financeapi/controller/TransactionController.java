package com.financeapi.controller;

import com.financeapi.domain.Transaction.TransactionType;
import com.financeapi.dto.request.TransactionRequest;
import com.financeapi.dto.response.AuditHistoryResponse;
import com.financeapi.dto.response.PagedResponse;
import com.financeapi.dto.response.TransactionResponse;
import com.financeapi.service.TransactionService;
import com.financeapi.service.impl.SseEmitterRegistry;
import com.financeapi.service.impl.TransactionServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @PostMapping
    @Operation(summary = "Create a transaction (supports Idempotency-Key header)")
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserDetails user) {

        TransactionServiceImpl.ANOMALY_WARNING.remove();
        TransactionServiceImpl.VELOCITY_SCORE.remove();
        TransactionResponse response = transactionService.create(request, idempotencyKey, user.getUsername());

        HttpHeaders headers = new HttpHeaders();
        String warning = TransactionServiceImpl.ANOMALY_WARNING.get();
        if (warning != null) {
            headers.set("X-Anomaly-Warning", "true");
            headers.set("X-Anomaly-Detail", warning);
        }
        Double velocityScore = TransactionServiceImpl.VELOCITY_SCORE.get();
        if (velocityScore != null) {
            headers.set("X-Velocity-Score", String.valueOf(Math.round(velocityScore)));
        }
        TransactionServiceImpl.ANOMALY_WARNING.remove();
        TransactionServiceImpl.VELOCITY_SCORE.remove();
        return ResponseEntity.ok().headers(headers).body(response);
    }

    @GetMapping
    @Operation(summary = "List transactions with optional filters, full-text search, and pagination")
    public ResponseEntity<PagedResponse<TransactionResponse>> getAll(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(transactionService.getAll(type, categoryId, from, to, search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a transaction (ANALYST or ADMIN)")
    public ResponseEntity<TransactionResponse> update(@PathVariable Long id,
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(transactionService.update(id, request, user.getUsername()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a transaction (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        transactionService.delete(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deleted")
    @Operation(summary = "View soft-deleted transactions (ADMIN only)")
    public ResponseEntity<PagedResponse<TransactionResponse>> getDeleted(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(transactionService.getDeleted(PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a soft-deleted transaction (ADMIN only)")
    public ResponseEntity<TransactionResponse> restore(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(transactionService.restore(id, user.getUsername()));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Audit replay — full state evolution of a transaction (time-travel)")
    public ResponseEntity<AuditHistoryResponse> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getHistory(id));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live transaction feed via Server-Sent Events")
    public SseEmitter stream() {
        return sseEmitterRegistry.register();
    }

    @GetMapping("/export")
    @Operation(summary = "Export transactions as CSV or Excel")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format) {
        if ("excel".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(transactionService.exportExcel());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(transactionService.exportCsv());
    }
}
