package com.financeapi.controller;

import com.financeapi.service.impl.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchant Intelligence")
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping("/top")
    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','ADMIN')")
    @Operation(summary = "Top merchants by spend. period=monthly (default) or weekly")
    public ResponseEntity<List<Map<String, Object>>> getTopMerchants(
            @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(merchantService.getTopMerchants(period));
    }
}
