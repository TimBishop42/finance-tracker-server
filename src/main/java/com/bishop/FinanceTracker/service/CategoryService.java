package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Category;
import com.bishop.FinanceTracker.repository.CategoryRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private Cache<String, Category> categoryCache;

    @PostConstruct
    public void init() {
        categoryCache = Caffeine.newBuilder()
                .maximumSize(100)
                .build();
        getAllCategories().stream().forEach(c -> categoryCache.put(c.getCategoryName(), c));
    }

    @Cacheable("categories")
    public List<Category> getAllCategories() {
        long startTime = System.currentTimeMillis();
        List<Category> categories = new ArrayList<>(categoryCache.asMap().values());
        log.info("Successfully retrieved categories in {} milliseconds", System.currentTimeMillis() - startTime);
        return categories;
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
        categoryCache.put(newCategory.getCategoryName(), newCategory);
        log.info("Successfully saved new category with name: {} in {} milliseconds", newCategory.getCategoryName(), System.currentTimeMillis() - startTime);
        return "Add SUCCESS: New category name saved";
    }
}
