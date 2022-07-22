package com.bishop.FinanceTracker.client;

import com.bishop.FinanceTracker.model.SaveTransactionResponse;
import com.bishop.FinanceTracker.model.json.TransactionJson;
import com.bishop.FinanceTracker.model.json.TransactionsJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Service
public class TestWebClient {

    WebClient webClient = WebClient.create("http://localhost:8080");

    public void triggerFlux() {
        webClient.get()
                .uri("/finance/flux-test")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(Integer.class)
                .map(s -> String.valueOf(s))
                .subscribe(msg -> {
                    log.info(msg);
                });
    }

    public void triggerTranSave() {
        TransactionJson t1 = TransactionJson.builder()
                .amount("1.5")
                .category("Food")
                .essential(false)
                .comment("test")
                .transactionDate(1658272761L)
                .build();
        TransactionJson t2 = TransactionJson.builder()
                .amount("1.5")
                .category("BabyStuff")
                .essential(false)
                .comment("test")
                .transactionDate(1658272761L)
                .build();
        TransactionJson t3 = TransactionJson.builder()
                .amount("1.5")
                .category("Coffee")
                .essential(false)
                .comment("test")
                .transactionDate(1658272761L)
                .build();
        TransactionsJson ts = TransactionsJson.builder()
                .transactionJsonList(List.of(t1, t2, t3)).build();
        webClient.post()
                .uri("/finance/submit-transaction-batch")
                .body(BodyInserters.fromValue(ts))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(SaveTransactionResponse.class)
                .subscribe(msg -> {
                    log.info(msg.getMessage());
                });
    }
}

