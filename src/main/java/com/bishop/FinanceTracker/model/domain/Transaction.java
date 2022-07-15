package com.bishop.FinanceTracker.model.domain;

import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Builder
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue
    private Long transactionId;

    @Column(nullable = false)
    private String category;

    @Column
    private BigDecimal amount;

    @Column
    private Long transactionDate;

    private String comment;

    private boolean essential;

    private String companyName;

    public static Transaction from(TransactionJson transactionJson) {
        return Transaction.builder()
                .category(transactionJson.getCategory())
                .amount(BigDecimal.valueOf(Double.parseDouble(transactionJson.getAmount())))
                .transactionDate(Long.getLong(transactionJson.getTransactionDate()))
                .comment(transactionJson.getComment())
                .essential(transactionJson.isEssential())
                .companyName(transactionJson.getCompanyName())
                .build();
    }
}
