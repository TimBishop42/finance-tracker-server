package com.bishop.FinanceTracker.model.dto;

import com.bishop.FinanceTracker.model.CategorizedTransaction;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class PredictResponse {
    @JsonProperty("categorized_transactions")
    private List<CategorizedTransaction> categorizedTransactions;
} 