package com.bishop.FinanceTracker.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Data
public class TrainRequestDto {
    private List<TransactionDto> transactions;
    private List<String> categories;
    @JsonProperty("confidence_scores")
    private List<Float> confidenceScores;
    @JsonProperty("user_corrections")
    private Map<String, String> userCorrections;

    public TrainRequestDto() {
        this.transactions = new ArrayList<>();
        this.categories = new ArrayList<>();
        this.confidenceScores = new ArrayList<>();
        this.userCorrections = new HashMap<>();
    }

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