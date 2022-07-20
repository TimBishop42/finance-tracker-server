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
import java.time.ZoneId;

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

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Long transactionDateTime;

    @Column(nullable = false)
    private String transactionDate;

    private String comment;

    private boolean essential;

    private Long createTime;

    public static Transaction from(TransactionJson transactionJson) {
        return Transaction.builder()
                .category(transactionJson.getCategory())
                .amount(BigDecimal.valueOf(Double.parseDouble(transactionJson.getAmount())))
                .transactionDateTime(transactionJson.getTransactionDate())
                .transactionDate(DateUtil.getLocalizedDateString(transactionJson.getTransactionDate(), ZoneId.of("Australia/Sydney")))
                .comment(transactionJson.getComment())
                .essential(transactionJson.isEssential())
                .createTime(System.currentTimeMillis())
                .build();
    }
}
