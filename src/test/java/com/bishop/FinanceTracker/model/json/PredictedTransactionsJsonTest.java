package com.bishop.FinanceTracker.model.json;

import com.bishop.FinanceTracker.model.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PredictedTransactionsJsonTest {

    private PredictedTransactionJson predicted(String type) {
        return PredictedTransactionJson.builder()
                .predictedCategory("Miscellaneous")
                .userCorrectedCategory("Miscellaneous")
                .amount("100.00")
                .transactionDate(1_700_000_000_000L)
                .transactionBusiness("CBA CREDIT CARD PAYMENT")
                .essential(false)
                .transactionType(type)
                .build();
    }

    @Test
    void carriesTransactionTypeThroughBatchConversion() {
        PredictedTransactionsJson batch = new PredictedTransactionsJson();
        batch.setTransactionJsonList(List.of(predicted("NEUTRAL")));

        TransactionsJson converted = batch.toTransactionsJson();
        assertEquals("NEUTRAL", converted.getTransactionJsonList().get(0).getTransactionType());
    }

    @Test
    void neutralSurvivesAllTheWayToTheEntity() {
        PredictedTransactionsJson batch = new PredictedTransactionsJson();
        batch.setTransactionJsonList(List.of(predicted("NEUTRAL")));

        Transaction t = Transaction.from(batch.toTransactionsJson().getTransactionJsonList().get(0));
        assertEquals("NEUTRAL", t.getTransactionType());
    }
}
