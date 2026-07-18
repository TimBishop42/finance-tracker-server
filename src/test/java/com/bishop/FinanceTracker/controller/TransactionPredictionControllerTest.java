package com.bishop.FinanceTracker.controller;

import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.bishop.FinanceTracker.model.TransactionRaw;
import com.bishop.FinanceTracker.service.TransactionPredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class TransactionPredictionControllerTest {

    @Mock
    private TransactionPredictionService predictionService;

    private TransactionPredictionController controller;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new TransactionPredictionController(predictionService);
    }

    @Test
    public void nullBodyReturnsBadRequestInsteadOfThrowing() {
        ResponseEntity<List<CategorizedTransaction>> response = controller.predictBatch(null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(predictionService, never()).predictBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    public void emptyBodyReturnsBadRequest() {
        ResponseEntity<List<CategorizedTransaction>> response = controller.predictBatch(Collections.emptyList());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(predictionService, never()).predictBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    public void nonEmptyBodyIsDelegatedToService() {
        List<TransactionRaw> raw = Collections.singletonList(new TransactionRaw());
        controller.predictBatch(raw);

        verify(predictionService).predictBatch(raw);
    }
}
