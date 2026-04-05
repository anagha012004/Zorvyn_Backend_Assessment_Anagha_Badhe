package com.financeapi.dto.response;

import com.financeapi.domain.Category;
import lombok.Data;

@Data
public class CategoryResponse {
    private Long id;
    private String name;
    private String colorHex;
    private String icon;

    public static CategoryResponse from(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.id = c.getId();
        r.name = c.getName();
        r.colorHex = c.getColorHex();
        r.icon = c.getIcon();
        return r;
    }
}
