package com.bishop.FinanceTracker.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@Builder
public class SaveTransactionResponse {
    HttpStatus status;
    String message;
    String error;
    String data;

    public static SaveTransactionResponse success(String message, String data, String error) {
        return SaveTransactionResponse.builder().status(HttpStatus.OK).message(message).data(data).error(error).build();
    }

    public static SaveTransactionResponse badRequest(String message, String data, String error) {
        return SaveTransactionResponse.builder().status(HttpStatus.BAD_REQUEST).message(message).data(data).error(error).build();
    }
    public static SaveTransactionResponse badRequest(String message, String data) {
        return SaveTransactionResponse.builder().status(HttpStatus.BAD_REQUEST).message(message).data(data).build();
    }
    public static SaveTransactionResponse serverError(String message, String data) {
        return SaveTransactionResponse.builder().status(HttpStatus.BAD_REQUEST).message(message).data(data).build();
    }
}
