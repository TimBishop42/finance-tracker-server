package com.bishop.FinanceTracker.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingResponse {
    private boolean success;
    private String message;
    private int transactionCount;
} 