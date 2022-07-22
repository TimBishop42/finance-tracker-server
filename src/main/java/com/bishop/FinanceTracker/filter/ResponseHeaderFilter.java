package com.bishop.FinanceTracker.filter;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class ResponseHeaderFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse()
                .getHeaders()
                .addAll(getHeaderMap());
        return chain.filter(exchange);
    }

    private MultiValueMap getHeaderMap() {
        Map headerMap = Map.of("Access-Control-Allow-Origin", List.of("*"), "Access-Control-Allow-Headers", List.of("Access-Control-Allow-Headers", "Origin", "Accept", "X-Requested-With", "Content-Type", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        return CollectionUtils.toMultiValueMap(headerMap);
    }
}