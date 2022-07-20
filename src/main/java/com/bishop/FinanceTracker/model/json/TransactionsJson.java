package com.bishop.FinanceTracker.model.json;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionsJson {

    List<TransactionJson> transactionJsonList;
}
