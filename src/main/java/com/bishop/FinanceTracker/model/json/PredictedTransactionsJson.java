package com.bishop.FinanceTracker.model.json;

import lombok.Data;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class PredictedTransactionsJson {
    private List<PredictedTransactionJson> transactionJsonList;
    private boolean dryRun;

    public TransactionsJson toTransactionsJson() {
        List<TransactionJson> convertedList = transactionJsonList.stream()
            .map(predicted -> TransactionJson.builder()
                .category(predicted.getUserCorrectedCategory() != null ? 
                    predicted.getUserCorrectedCategory() : 
                    predicted.getPredictedCategory())
                .amount(predicted.getAmount())
                .transactionDate(predicted.getTransactionDate())
                .comment(predicted.getComment())
                .businessName(predicted.getTransactionBusiness())
                .essential(predicted.isEssential())
                .duplicateReviewed(predicted.isDuplicateReviewed())
                .build())
            .collect(Collectors.toList());

        return TransactionsJson.builder()
            .transactionJsonList(convertedList)
            .dryRun(dryRun)
            .build();
    }
}
