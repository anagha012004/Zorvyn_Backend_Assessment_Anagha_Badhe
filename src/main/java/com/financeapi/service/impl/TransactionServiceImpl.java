package com.financeapi.service.impl;

import com.financeapi.domain.Category;
import com.financeapi.domain.Transaction;
import com.financeapi.domain.Transaction.TransactionType;
import com.financeapi.domain.User;
import com.financeapi.dto.request.TransactionRequest;
import com.financeapi.dto.response.AuditHistoryResponse;
import com.financeapi.dto.response.AuditHistoryResponse.HistoryEntry;
import com.financeapi.dto.response.PagedResponse;
import com.financeapi.dto.response.TransactionResponse;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.AuditLogRepository;
import com.financeapi.repository.CategoryRepository;
import com.financeapi.repository.TransactionRepository;
import com.financeapi.repository.UserRepository;
import com.financeapi.service.TransactionService;
import com.financeapi.util.TransactionSpecification;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final VelocityService velocityService;
    private final DnaFingerprintService dnaFingerprintService;
    private final MerchantService merchantService;
    private final SseEmitterRegistry sseEmitterRegistry;

    // Stored in ThreadLocal so the controller can read it for response headers
    public static final ThreadLocal<String> ANOMALY_WARNING = new ThreadLocal<>();
    public static final ThreadLocal<Double> VELOCITY_SCORE = new ThreadLocal<>();

    @Override
    @Transactional
    @CacheEvict(value = "dashboard-summary", allEntries = true)
    public TransactionResponse create(TransactionRequest request, String idempotencyKey, String userEmail) {
        if (idempotencyKey != null) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .map(TransactionResponse::from)
                    .orElseGet(() -> doCreate(request, idempotencyKey, userEmail));
        }
        return doCreate(request, null, userEmail);
    }

    private TransactionResponse doCreate(TransactionRequest request, String idempotencyKey, String userEmail) {
        User user = getUser(userEmail);
        Transaction t = new Transaction();
        t.setAmount(request.getAmount());
        t.setType(request.getType());
        t.setDate(request.getDate());
        t.setNotes(request.getNotes());
        t.setCreatedBy(user);
        t.setIdempotencyKey(idempotencyKey);
        if (request.getCategoryId() != null) {
            t.setCategory(getCategory(request.getCategoryId()));
            checkAnomaly(user.getId(), request.getCategoryId(), request.getAmount());
        }

        Transaction saved = transactionRepository.save(t);
        TransactionResponse response = TransactionResponse.from(saved);

        // Off-hours detection
        LocalDateTime now = LocalDateTime.now(ZoneId.of(user.getTimezone()));
        int hour = now.getHour();
        response.setOffHours(hour >= 1 && hour < 5);

        // DNA fingerprinting
        boolean isDuplicate = dnaFingerprintService.checkAndStore(
                saved, user.getId(), request.getAmount(),
                request.getCategoryId() != null ? request.getCategoryId() : 0L,
                saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now());
        response.setPossibleDuplicate(isDuplicate);

        // Velocity scoring
        double score = velocityService.computeAndUpdate(user, request.getAmount());
        VELOCITY_SCORE.set(score);

        // Merchant tagging
        merchantService.extractAndTag(saved);
        merchantService.findByTransactionId(saved.getId())
                .ifPresent(tag -> response.setMerchantName(tag.getMerchantName()));

        // SSE broadcast
        sseEmitterRegistry.broadcast(response);

        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "dashboard-summary", allEntries = true)
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public TransactionResponse update(Long id, TransactionRequest request, String userEmail) {
        Transaction t = getActive(id);
        t.setAmount(request.getAmount());
        t.setType(request.getType());
        t.setDate(request.getDate());
        t.setNotes(request.getNotes());
        if (request.getCategoryId() != null) t.setCategory(getCategory(request.getCategoryId()));
        return TransactionResponse.from(transactionRepository.save(t));
    }

    @Override
    @Transactional
    @CacheEvict(value = "dashboard-summary", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id, String userEmail) {
        Transaction t = getActive(id);
        t.setDeleted(true);
        transactionRepository.save(t);
    }

    @Override
    public TransactionResponse getById(Long id) {
        return TransactionResponse.from(getActive(id));
    }

    @Override
    public PagedResponse<TransactionResponse> getAll(TransactionType type, Long categoryId,
                                                      LocalDate from, LocalDate to, Pageable pageable) {
        Page<TransactionResponse> page = transactionRepository
                .findAll(TransactionSpecification.filter(type, categoryId, from, to), pageable)
                .map(TransactionResponse::from);
        return PagedResponse.from(page);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<TransactionResponse> getDeleted(Pageable pageable) {
        return PagedResponse.from(transactionRepository.findByDeletedTrue(pageable).map(TransactionResponse::from));
    }

    @Override
    @Transactional
    @CacheEvict(value = "dashboard-summary", allEntries = true)
    @PreAuthorize("hasRole('ADMIN')")
    public TransactionResponse restore(Long id, String userEmail) {
        Transaction t = transactionRepository.findById(id)
                .filter(Transaction::isDeleted)
                .orElseThrow(() -> new ResourceNotFoundException("Deleted transaction not found: " + id));
        t.setDeleted(false);
        return TransactionResponse.from(transactionRepository.save(t));
    }

    @Override
    public AuditHistoryResponse getHistory(Long id) {
        // Verify transaction exists
        transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
        List<HistoryEntry> entries = auditLogRepository.findHistory("Transaction", String.valueOf(id))
                .stream()
                .map(a -> new HistoryEntry(a.getAction(), a.getOldValue(), a.getNewValue(), a.getTimestamp()))
                .collect(Collectors.toList());
        return new AuditHistoryResponse(id, entries);
    }

    @Override
    public byte[] exportCsv() {
        List<Transaction> all = transactionRepository.findAll(
                TransactionSpecification.filter(null, null, null, null));
        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{"ID", "Amount", "Type", "Category", "Date", "Notes", "CreatedBy"});
            for (Transaction t : all) {
                writer.writeNext(new String[]{
                        String.valueOf(t.getId()), t.getAmount().toString(), t.getType().name(),
                        t.getCategory() != null ? t.getCategory().getName() : "",
                        t.getDate().toString(), t.getNotes() != null ? t.getNotes() : "",
                        t.getCreatedBy().getFullName()
                });
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV export failed", e);
        }
        return sw.toString().getBytes();
    }

    @Override
    public byte[] exportExcel() {
        List<Transaction> all = transactionRepository.findAll(
                TransactionSpecification.filter(null, null, null, null));
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Transactions");
            Row header = sheet.createRow(0);
            String[] cols = {"ID", "Amount", "Type", "Category", "Date", "Notes", "CreatedBy"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
            int rowNum = 1;
            for (Transaction t : all) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(t.getId());
                row.createCell(1).setCellValue(t.getAmount().doubleValue());
                row.createCell(2).setCellValue(t.getType().name());
                row.createCell(3).setCellValue(t.getCategory() != null ? t.getCategory().getName() : "");
                row.createCell(4).setCellValue(t.getDate().toString());
                row.createCell(5).setCellValue(t.getNotes() != null ? t.getNotes() : "");
                row.createCell(6).setCellValue(t.getCreatedBy().getFullName());
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Excel export failed", e);
        }
    }

    private void checkAnomaly(Long userId, Long categoryId, BigDecimal amount) {
        transactionRepository.avgAmountByUserAndCategory(userId, categoryId).ifPresent(avg -> {
            transactionRepository.stddevAmountByUserAndCategory(userId, categoryId).ifPresent(stddev -> {
                if (stddev > 0 && amount.subtract(avg).abs().doubleValue() > 3 * stddev) {
                    ANOMALY_WARNING.set("Transaction amount deviates significantly from your historical average");
                }
            });
        });
    }

    private Transaction getActive(Long id) {
        return transactionRepository.findById(id)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }
}
