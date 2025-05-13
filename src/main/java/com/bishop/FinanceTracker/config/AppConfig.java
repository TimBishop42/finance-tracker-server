package com.bishop.FinanceTracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Value("${ml.service.url:http://localhost:8000/api/v1/predict/batch}")
    private String mlServiceUrl;

    @Bean
    public String mlServiceUrl() {
        return mlServiceUrl;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
} 