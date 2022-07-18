package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Category;
import com.bishop.FinanceTracker.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Mono<List<Category>> getAllCategories() {
        long startTime = System.currentTimeMillis();
        List<Category> categories = categoryRepository.findAll();
        log.info("Successfully retrieved categories in {} milliseconds", System.currentTimeMillis() - startTime);
        return Mono.just(categories);
    }

    public String addCategory(String category) {
        long startTime = System.currentTimeMillis();
        if (isNull(category)) {
            return "Add FAILED: New category string cannot be null";
        }
        Optional<Category> record = categoryRepository.findById(category);
        if (record.isPresent()) {
            return "Add FAILED: Category already exists with that name";
        }
        Category newCategory = Category.builder()
                .categoryName(category)
                .createDate(System.currentTimeMillis())
                .build();
        categoryRepository.save(newCategory);
        log.info("Successfully saved new category with name: {} in {} milliseconds", newCategory.getCategoryName(), System.currentTimeMillis() - startTime);
        return "Add SUCCESS: New category name saved";
    }
}
