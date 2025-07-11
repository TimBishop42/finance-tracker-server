package com.bishop.FinanceTracker.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@Log4j2
@Configuration
@EnableJpaRepositories(basePackages = "com.bishop.FinanceTracker.repository")
@EnableTransactionManagement
public class DatabaseConfig {

    private final Environment env;

    public DatabaseConfig(Environment env) {
        this.env = env;
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            String schema = env.acceptsProfiles("test") ? "public" : "finance";
            log.info("Using schema: {} for profiles: {}", schema, String.join(", ", env.getActiveProfiles()));
            hibernateProperties.put("hibernate.default_schema", schema);
        };
    }
}