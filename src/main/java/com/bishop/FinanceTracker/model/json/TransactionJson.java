package com.bishop.FinanceTracker.model.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class TransactionJson {

    @NotNull
    private String category;

    @NotNull
    private String amount;

    @NotNull
    @JsonProperty(value = "transactionDate")
    private String transactionDate;

    private String comment;

    @Deprecated
    private String companyName;

    @NotNull
    private boolean essential;

}
