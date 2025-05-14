package com.bishop.FinanceTracker.model.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
public class PredictedTransactionJson {

    @NotBlank
    private String predictedCategory;

    private String userCorrectedCategory;

    @NotNull
    private String amount;

    @NotNull
    @JsonProperty(value = "transactionDate")
    private Long transactionDate;

    private String comment;

    @Deprecated
    private String companyName;

    @NotNull
    private boolean essential;

}
