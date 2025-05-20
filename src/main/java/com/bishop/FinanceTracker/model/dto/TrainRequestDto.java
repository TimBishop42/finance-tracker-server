package com.bishop.FinanceTracker.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TrainRequestDto {
    private List<TransactionDto> transactions;
    private List<String> categories;
    @JsonProperty("confidence_scores")
    private List<Float> confidenceScores;
    @JsonProperty("user_corrections")
    private Map<Integer, String> userCorrections;

    @Data
    public static class TransactionDto {
        @JsonProperty("transaction_id")
        private int transactionId;
        private String date;
        private float amount;
        @JsonProperty("business_name")
        private String businessName;
        private String comment;
    }
} 