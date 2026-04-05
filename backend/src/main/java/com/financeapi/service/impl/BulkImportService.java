package com.financeapi.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financeapi.domain.Category;
import com.financeapi.domain.Transaction;
import com.financeapi.domain.User;
import com.financeapi.dto.request.TransactionRequest;
import com.financeapi.dto.response.BulkImportResult;
import com.financeapi.exception.BadRequestException;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.CategoryRepository;
import com.financeapi.repository.TransactionRepository;
import com.financeapi.repository.UserRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Imports transactions from a CSV file.
     * Expected columns (header row required): amount,type,categoryId,date,notes
     * The entire import is wrapped in a single transaction — any validation failure
     * rolls back all rows (strict mode). Errors are collected and returned.
     */
    @Transactional
    @CacheEvict(value = "dashboard-summary", allEntries = true)
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public BulkImportResult importCsv(MultipartFile file, String userEmail) {
        User user = getUser(userEmail);
        List<String> errors = new ArrayList<>();
        List<Transaction> toSave = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            String[] header = reader.readNext(); // skip header
            if (header == null) throw new BadRequestException("CSV file is empty");

            String[] row;
            int line = 1;
            while ((row = reader.readNext()) != null) {
                line++;
                try {
                    toSave.add(buildTransaction(row[0], row[1],
                            row.length > 2 ? row[2] : null,
                            row.length > 3 ? row[3] : null,
                            row.length > 4 ? row[4] : null,
                            user));
                } catch (Exception e) {
                    errors.add("Row " + line + ": " + e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new BadRequestException("Failed to parse CSV: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            // Rollback by throwing — @Transactional will undo any saves
            throw new BadRequestException("Import aborted due to validation errors: " + errors);
        }

        transactionRepository.saveAll(toSave);
        BulkImportResult result = new BulkImportResult();
        result.setImported(toSave.size());
        result.setFailed(0);
        result.setErrors(List.of());
        return result;
    }

    /**
     * Imports transactions from a JSON array.
     * Uses the same TransactionRequest schema as POST /api/v1/transactions.
     */
    @Transactional
    @CacheEvict(value = "dashboard-summary", allEntries = true)
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public BulkImportResult importJson(MultipartFile file, String userEmail) {
        User user = getUser(userEmail);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        List<TransactionRequest> requests;
        try {
            requests = mapper.readValue(file.getInputStream(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new BadRequestException("Failed to parse JSON: " + e.getMessage());
        }

        List<String> errors = new ArrayList<>();
        List<Transaction> toSave = new ArrayList<>();
        int idx = 0;
        for (TransactionRequest req : requests) {
            idx++;
            try {
                if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0)
                    throw new IllegalArgumentException("amount must be > 0");
                if (req.getType() == null) throw new IllegalArgumentException("type is required");
                if (req.getDate() == null) throw new IllegalArgumentException("date is required");
                toSave.add(buildTransactionFromRequest(req, user));
            } catch (Exception e) {
                errors.add("Item " + idx + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException("Import aborted due to validation errors: " + errors);
        }

        transactionRepository.saveAll(toSave);
        BulkImportResult result = new BulkImportResult();
        result.setImported(toSave.size());
        result.setFailed(0);
        result.setErrors(List.of());
        return result;
    }

    private Transaction buildTransaction(String amountStr, String typeStr,
                                          String categoryIdStr, String dateStr,
                                          String notes, User user) {
        BigDecimal amount = new BigDecimal(amountStr.trim());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be > 0");

        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setType(Transaction.TransactionType.valueOf(typeStr.trim().toUpperCase()));
        t.setDate(dateStr != null && !dateStr.isBlank() ? LocalDate.parse(dateStr.trim()) : LocalDate.now());
        t.setNotes(notes);
        t.setCreatedBy(user);
        if (categoryIdStr != null && !categoryIdStr.isBlank()) {
            Long catId = Long.parseLong(categoryIdStr.trim());
            Category cat = categoryRepository.findById(catId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + catId));
            t.setCategory(cat);
        }
        return t;
    }

    private Transaction buildTransactionFromRequest(TransactionRequest req, User user) {
        Transaction t = new Transaction();
        t.setAmount(req.getAmount());
        t.setType(req.getType());
        t.setDate(req.getDate());
        t.setNotes(req.getNotes());
        t.setCreatedBy(user);
        if (req.getCategoryId() != null) {
            Category cat = categoryRepository.findById(req.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + req.getCategoryId()));
            t.setCategory(cat);
        }
        return t;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
