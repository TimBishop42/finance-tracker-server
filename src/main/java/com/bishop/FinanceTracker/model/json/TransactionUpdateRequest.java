package com.bishop.FinanceTracker.model.json;

import lombok.Data;

/**
 * Partial-update payload for an existing transaction (review/edit UI). Any null
 * field is left unchanged; the transaction is located by {@code transactionId}.
 */
@Data
public class TransactionUpdateRequest {

    private Long transactionId;
    private String category;
    /** Decimal string magnitude, e.g. "42.50". */
    private String amount;
    /** Epoch millis of the transaction date. */
    private Long transactionDate;
    private String comment;
    private String businessName;
    private Boolean essential;
    private String transactionType;
}
