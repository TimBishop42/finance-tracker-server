package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.wealth.PriceQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Free, key-less price feed using Yahoo Finance's chart endpoint. Covers ASX
 * ({@code CBA.AX}), NASDAQ/NYSE (bare ticker), and FX pairs ({@code USDAUD=X}).
 * Unofficial — sends a browser User-Agent and uses short timeouts; failures
 * return empty rather than throwing.
 */
@Slf4j
@Component
public class YahooPriceProvider implements PriceProvider {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public YahooPriceProvider(
            @Value("${app.yahoo.base-url:https://query1.finance.yahoo.com}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String name() {
        return "YAHOO";
    }

    @Override
    public Optional<PriceQuote> fetch(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        try {
            String url = baseUrl + "/v8/finance/chart/" + symbol.trim() + "?range=1d&interval=1d";
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (finance-tracker)");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<String> resp =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return Optional.empty();
            return parseQuote(resp.getBody());
        } catch (Exception e) {
            log.warn("Yahoo price fetch failed for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /** Parse a chart response into a quote. Package-private for unit testing. */
    Optional<PriceQuote> parseQuote(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return Optional.empty();
            JsonNode meta = result.get(0).path("meta");
            JsonNode priceNode = meta.path("regularMarketPrice");
            if (priceNode.isMissingNode() || priceNode.isNull()) return Optional.empty();

            BigDecimal price = new BigDecimal(priceNode.asText());
            String currency = meta.path("currency").asText("");
            long epoch = meta.path("regularMarketTime").asLong(0);
            String asOf = epoch > 0
                    ? Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    : LocalDate.now().toString();
            return Optional.of(new PriceQuote(price, currency, asOf));
        } catch (Exception e) {
            log.warn("Failed to parse Yahoo quote: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
