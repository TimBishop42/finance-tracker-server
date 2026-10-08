package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Category;
import com.bishop.FinanceTracker.model.json.CategoryRequest;
import com.bishop.FinanceTracker.repository.CategoryRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CategoryServiceBudgetTest {

    private CategoryRepository repository;
    private CategoryService service;
    private Category groceries;

    @BeforeEach
    public void setup() {
        repository = mock(CategoryRepository.class);
        groceries = Category.builder().categoryName("Groceries").createDate(1L).build();
        when(repository.findAll()).thenReturn(List.of(groceries));
        // Fresh copy per lookup, so the cache assertions only pass if setBudget re-caches the saved entity.
        when(repository.findById("Groceries")).thenAnswer(inv -> Optional.of(groceries.toBuilder().build()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new CategoryService(repository, Validation.buildDefaultValidatorFactory().getValidator());
        service.init();
    }

    @Test
    public void setsBudgetAndRefreshesCache() {
        service.setBudget(request("Groceries", "1200.50"));

        assertEquals(new BigDecimal("1200.50"), service.getCategory("Groceries").orElseThrow().getMonthlyBudget());
    }

    @Test
    public void nullBudgetClearsIt() {
        service.setBudget(request("Groceries", "500"));
        service.setBudget(request("Groceries", null));

        assertNull(service.getCategory("Groceries").orElseThrow().getMonthlyBudget());
    }

    @Test
    public void rejectsNegativeBudget() {
        assertThrows(IllegalArgumentException.class, () -> service.setBudget(request("Groceries", "-1")));
        verify(repository, never()).save(any());
    }

    @Test
    public void rejectsBudgetBeyondColumnPrecision() {
        assertThrows(IllegalArgumentException.class, () -> service.setBudget(request("Groceries", "1000000000")));
        assertThrows(IllegalArgumentException.class, () -> service.setBudget(request("Groceries", "10.001")));
        verify(repository, never()).save(any());
    }

    @Test
    public void rejectsUnknownCategory() {
        assertThrows(IllegalArgumentException.class, () -> service.setBudget(request("Nope", "10")));
    }

    private static CategoryRequest request(String name, String budget) {
        CategoryRequest r = new CategoryRequest();
        r.setCategoryName(name);
        r.setMonthlyBudget(budget == null ? null : new BigDecimal(budget));
        return r;
    }
}
