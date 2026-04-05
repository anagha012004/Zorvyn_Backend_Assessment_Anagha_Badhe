package com.financeapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a valid hex color e.g. #FF6B6B")
    private String colorHex;

    @Size(max = 50)
    private String icon;
}
