package com.bishop.FinanceTracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

/** Services' IllegalArgumentException reaches the client as a 400 with its message. */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ApiExceptionHandlerIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Test
    void unknownCategoryBudgetIsBadRequestWithMessage() {
        client.post().uri("/api/finance/set-category-budget")
                .bodyValue(Map.of("categoryName", "NoSuchCategory", "monthlyBudget", 10))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("Category not found: NoSuchCategory");
    }

    @Test
    void nonNumericSalaryAmountIsBadRequest() {
        client.post().uri("/api/finance/salary-history")
                .bodyValue(Map.of("date", "2026-01-01", "amount", "lots"))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
