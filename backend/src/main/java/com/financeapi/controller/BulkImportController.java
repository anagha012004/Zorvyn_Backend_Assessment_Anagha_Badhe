package com.financeapi.controller;

import com.financeapi.dto.response.BulkImportResult;
import com.financeapi.service.impl.BulkImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/transactions/import")
@RequiredArgsConstructor
@Tag(name = "Transactions")
public class BulkImportController {

    private final BulkImportService bulkImportService;

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import transactions from CSV (ANALYST+). " +
                         "Header row: amount,type,categoryId,date,notes. " +
                         "Any validation error rolls back the entire import.")
    public ResponseEntity<BulkImportResult> importCsv(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(bulkImportService.importCsv(file, user.getUsername()));
    }

    @PostMapping(value = "/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import transactions from JSON array (ANALYST+). " +
                         "Uses the same schema as POST /api/v1/transactions. " +
                         "Any validation error rolls back the entire import.")
    public ResponseEntity<BulkImportResult> importJson(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(bulkImportService.importJson(file, user.getUsername()));
    }
}
