package com.bishop.FinanceTracker.config;


import com.bishop.FinanceTracker.model.domain.Category;
import com.bishop.FinanceTracker.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDataConfig {

    private final CategoryRepository categoryRepository;

    private static final List<String> testData = List.of("Coffee", "Alcohol", "Eating Out", "Chocolate", "Pet Food", "Miscellaneous", "Fuel", "Bills", "Baby");

    @PostConstruct
    public void initData() {
        List<Category> categories = testData.stream().map(name -> Category.builder()
                .categoryName(name)
                .createDate(System.currentTimeMillis())
                .build())
                .collect(Collectors.toList());
        categoryRepository.saveAll(categories);
    }
}
