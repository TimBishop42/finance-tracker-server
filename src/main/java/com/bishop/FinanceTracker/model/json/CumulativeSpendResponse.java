package com.bishop.FinanceTracker.model.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CumulativeSpendResponse {
    @JsonProperty("cumulativeValues")
    private List<String> cumulativeValues;
} 