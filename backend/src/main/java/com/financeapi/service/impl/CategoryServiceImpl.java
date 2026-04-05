package com.financeapi.service.impl;

import com.financeapi.domain.Category;
import com.financeapi.dto.request.CategoryRequest;
import com.financeapi.dto.response.CategoryResponse;
import com.financeapi.exception.BadRequestException;
import com.financeapi.exception.ResourceNotFoundException;
import com.financeapi.repository.CategoryRepository;
import com.financeapi.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName()))
            throw new BadRequestException("Category already exists: " + request.getName());
        Category c = new Category();
        c.setName(request.getName());
        c.setColorHex(request.getColorHex());
        c.setIcon(request.getIcon());
        return CategoryResponse.from(categoryRepository.save(c));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        if (!c.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName()))
            throw new BadRequestException("Category already exists: " + request.getName());
        c.setName(request.getName());
        c.setColorHex(request.getColorHex());
        c.setIcon(request.getIcon());
        return CategoryResponse.from(categoryRepository.save(c));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        if (!categoryRepository.existsById(id))
            throw new ResourceNotFoundException("Category not found: " + id);
        categoryRepository.deleteById(id);
    }
}
