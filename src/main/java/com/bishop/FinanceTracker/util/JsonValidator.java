package com.bishop.FinanceTracker.util;

import com.bishop.FinanceTracker.model.json.TransactionJson;
import org.springframework.stereotype.Service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

@Service
public class JsonValidator {

    private ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

    public Set<ConstraintViolation<TransactionJson>> validateJson(TransactionJson input) {
        return factory.getValidator().validate(input);
    }
}
