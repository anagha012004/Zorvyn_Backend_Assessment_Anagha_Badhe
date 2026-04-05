package com.financeapi.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class BulkImportResult {
    private int imported;
    private int failed;
    private List<String> errors;
}
