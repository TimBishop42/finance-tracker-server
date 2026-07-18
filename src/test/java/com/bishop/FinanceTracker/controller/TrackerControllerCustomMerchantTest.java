package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.domain.CustomMerchant;
import com.bishop.FinanceTracker.repository.CustomMerchantRepository;
import com.bishop.FinanceTracker.repository.ExcludedMerchantRepository;
import com.bishop.FinanceTracker.repository.SalaryHistoryRepository;
import com.bishop.FinanceTracker.service.AggregationService;
import com.bishop.FinanceTracker.service.CategoryService;
import com.bishop.FinanceTracker.service.TransactionService;
import com.bishop.FinanceTracker.service.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class TrackerControllerCustomMerchantTest {

    @Mock private TransactionService transactionService;
    @Mock private CategoryService categoryService;
    @Mock private AggregationService aggregationService;
    @Mock private UserSettingsService userSettingsService;
    @Mock private ExcludedMerchantRepository excludedMerchantRepository;
    @Mock private CustomMerchantRepository customMerchantRepository;
    @Mock private SalaryHistoryRepository salaryHistoryRepository;

    private TrackerController controller;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new TrackerController(transactionService, categoryService, aggregationService,
                userSettingsService, excludedMerchantRepository, customMerchantRepository, salaryHistoryRepository);
    }

    @Test
    public void savesOrdinaryPattern() {
        ResponseEntity<Void> response = controller.addCustomMerchant(
                Map.of("merchantPattern", "netflix", "merchantType", "subscription"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(customMerchantRepository).save(new CustomMerchant("netflix", "subscription"));
    }

    @Test
    public void rejectsInvalidRegexSyntax() {
        ResponseEntity<Void> response = controller.addCustomMerchant(
                Map.of("merchantPattern", "(", "merchantType", "bill"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(customMerchantRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
