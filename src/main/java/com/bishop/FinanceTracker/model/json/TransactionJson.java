package com.bishop.FinanceTracker.model.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
public class TransactionJson {

    @NotBlank
    private String category;

    @NotNull
    private String amount;

    @NotNull
    @JsonProperty(value = "transactionDate")
    private Long transactionDate;

    private String comment;

    @JsonProperty(value = "transactionBusiness")
    private String businessName;

    @Deprecated
    private String companyName;

    @NotNull
    private boolean essential;

    private boolean duplicateReviewed;

    private String transactionType;
}
