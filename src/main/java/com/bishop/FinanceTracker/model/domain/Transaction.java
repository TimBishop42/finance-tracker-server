package com.bishop.FinanceTracker.model.domain;

import com.bishop.FinanceTracker.config.BooleanToIntegerConverter;
import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.util.DateUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.ZoneId;

@Data
@Entity(name = "transactions")
@Table(name = "transactions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(nullable = false)
    @Convert(converter = BooleanToIntegerConverter.class)
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
