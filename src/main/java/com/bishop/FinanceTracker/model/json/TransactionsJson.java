package com.bishop.FinanceTracker.model.json;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionsJson {

    List<TransactionJson> transactionJsonList;
    Boolean dryRun;
}
