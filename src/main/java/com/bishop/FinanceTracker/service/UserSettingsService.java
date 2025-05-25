package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.UserSettings;
import com.bishop.FinanceTracker.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {
    private final UserSettingsRepository repository;
    private static final BigDecimal DEFAULT_MAX_SPEND = new BigDecimal("12000.00");

    public BigDecimal getMaxSpendValue() {
        return repository.findById(1L)  // Using 1 as default user ID
            .map(UserSettings::getMaxSpendValue)
            .orElseGet(() -> {
                log.info("No max spend value found, using default: {}", DEFAULT_MAX_SPEND);
                return DEFAULT_MAX_SPEND;
            });
    }

    public void setMaxSpendValue(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Max spend value must be greater than 0");
        }

        UserSettings settings = repository.findById(1L)
            .orElseGet(() -> {
                UserSettings newSettings = new UserSettings();
                newSettings.setId(1L);  // Set ID for new records
                newSettings.setMaxSpendValue(value);
                return newSettings;
            });

        settings.setMaxSpendValue(value);
        repository.save(settings);
        log.info("Updated max spend value to: {}", value);
    }
} 