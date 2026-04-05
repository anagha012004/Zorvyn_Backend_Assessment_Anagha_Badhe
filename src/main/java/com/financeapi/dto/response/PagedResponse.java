package com.financeapi.dto.response;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
public class PagedResponse<T> {
    private List<T> content;
    private int currentPage;
    private int totalPages;
    private long totalElements;

    public static <T> PagedResponse<T> from(Page<T> page) {
        PagedResponse<T> r = new PagedResponse<>();
        r.content = page.getContent();
        r.currentPage = page.getNumber();
        r.totalPages = page.getTotalPages();
        r.totalElements = page.getTotalElements();
        return r;
    }
}
