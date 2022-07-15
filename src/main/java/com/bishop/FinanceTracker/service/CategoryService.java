package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Category;
import com.bishop.FinanceTracker.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private static final List<String> testData = List.of("Coffee","Alcohol","Eating Out","Chocolate","Pet Food","Miscellaneous","Fuel","Bills","Baby");

    public Mono<List<Category>> getAllCategories() {
        return Mono.just(categoryRepository.findAll());
    }

    public String addCategory(String category) {
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
        return "Add SUCCESS: New category name saved";
    }

    @PostConstruct
    public void initData(){
        List<Category> categories = testData.stream().map(name -> Category.builder()
                .categoryName(name)
                .createDate(System.currentTimeMillis())
                .build())
                .collect(Collectors.toList());
        categoryRepository.saveAll(categories);
    }
}