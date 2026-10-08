package com.bishop.FinanceTracker.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/**
 * Services signal invalid input with IllegalArgumentException; map it to a 400
 * carrying the message so controllers don't each repeat the try/catch. Anything
 * else falls through to Spring's default (logged) 500 handling.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e, ServerWebExchange exchange) {
        log.warn("Rejected {} {}: {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(), e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
