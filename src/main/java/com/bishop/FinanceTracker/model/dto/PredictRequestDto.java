package com.bishop.FinanceTracker.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class PredictRequestDto {
    private List<TransactionDto> transactions;
    private List<String> categories;

    @Data
    public static class TransactionDto {
        @JsonProperty("transaction_id")
        private int transactionId;
        private String date;
        private float amount;
        @JsonProperty("business_name")
        private String businessName;
    }
} 