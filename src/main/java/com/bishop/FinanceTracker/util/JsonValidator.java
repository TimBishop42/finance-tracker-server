package com.bishop.FinanceTracker.util;

import com.bishop.FinanceTracker.model.json.TransactionJson;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Set;

@Service
public class JsonValidator {

    private ValidatorFactory factory = Validation.buildDefaultValidatorFactory();


    public Set<ConstraintViolation<TransactionJson>> validateJson(TransactionJson input) {
        return factory.getValidator().validate(input);
    }
}
