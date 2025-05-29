package com.bishop.FinanceTracker.model;

import com.bishop.FinanceTracker.model.domain.Transaction;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SaveTransactionResponse {
    private HttpStatus status;
    private String message;
    private String requestPayload;
    private String errorDetails;
    private List<TransactionDuplicate> duplicates;
    private boolean hasDuplicates;

    @Data
    @Builder
    public static class TransactionDuplicate {
        private Transaction newTransaction;
        private List<Transaction> existingDuplicates;
    }

    public static SaveTransactionResponse success(String message, String requestPayload, String errorDetails) {
        return SaveTransactionResponse.builder()
            .status(HttpStatus.OK)
            .message(message)
            .requestPayload(requestPayload)
            .errorDetails(errorDetails)
            .hasDuplicates(false)
            .build();
    }

    public static SaveTransactionResponse badRequest(String message, String requestPayload, String errorDetails) {
        return SaveTransactionResponse.builder()
            .status(HttpStatus.BAD_REQUEST)
            .message(message)
            .requestPayload(requestPayload)
            .errorDetails(errorDetails)
            .hasDuplicates(false)
            .build();
    }

    public static SaveTransactionResponse serverError(String message, String requestPayload) {
        return SaveTransactionResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .message(message)
            .requestPayload(requestPayload)
            .hasDuplicates(false)
            .build();
    }

    public static SaveTransactionResponse withDuplicates(String message, String requestPayload,
                                                          Map<Transaction, List<Transaction>> duplicateMap) {
        List<TransactionDuplicate> duplicates = duplicateMap.entrySet().stream()
            .map(entry -> TransactionDuplicate.builder()
                .newTransaction(entry.getKey())
                .existingDuplicates(entry.getValue())
                .build())
            .collect(java.util.stream.Collectors.toList());

        return SaveTransactionResponse.builder()
            .status(HttpStatus.OK)
            .message(message)
            .requestPayload(requestPayload)
            .duplicates(duplicates)
            .hasDuplicates(true)
            .build();
    }
}
