package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.NeutralMerchant;
import com.bishop.FinanceTracker.repository.NeutralMerchantRepository;
import com.bishop.FinanceTracker.service.recurring.MerchantNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decides whether a transaction is a NEUTRAL internal transfer / credit-card
 * payment on CSV import. A merchant is neutral if it matches a user-saved rule
 * (by normalised name) or one of a few conservative built-in transfer patterns.
 *
 * <p>Matching reuses the same {@link MerchantNormalizer} as recurring detection
 * so a rule saved from one statement matches the same merchant on the next.
 * Seed patterns are intentionally tight (obvious card-payment / transfer wording)
 * to avoid neutralising genuine bills paid by BPAY or direct debit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NeutralClassificationService {

    private static final List<Pattern> SEED = List.of(
            Pattern.compile("payment\\s*[-–]?\\s*thank\\s*you", Pattern.CASE_INSENSITIVE),
            Pattern.compile("thank\\s*you\\s*for\\s*your\\s*payment", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcard(member)?\\s*payment\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcredit\\s*card\\s*payment\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcc\\s*payment\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpayment\\s*received\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(internal|funds?)\\s*transfer\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btransfer\\s*(to|from)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btfr\\b", Pattern.CASE_INSENSITIVE));

    private final NeutralMerchantRepository repository;
    private final MerchantNormalizer normalizer;

    /** Normalised key used both to store a rule and to match on import. */
    public String keyFor(String businessName) {
        return normalizer.normalize(businessName).key();
    }

    public boolean isNeutral(String businessName, Set<String> ruleKeys) {
        if (businessName == null || businessName.isBlank()) {
            return false;
        }
        for (Pattern p : SEED) {
            if (p.matcher(businessName).find()) {
                return true;
            }
        }
        return ruleKeys.contains(keyFor(businessName));
    }

    /** Return the subset of the given business names that should be booked NEUTRAL. */
    public Set<String> neutralNames(List<String> names) {
        Set<String> ruleKeys = repository.findAll().stream()
                .map(NeutralMerchant::getMerchantKey)
                .collect(Collectors.toSet());
        Set<String> out = new LinkedHashSet<>();
        for (String name : names) {
            if (isNeutral(name, ruleKeys)) {
                out.add(name);
            }
        }
        return out;
    }

    public List<String> listKeys() {
        return repository.findAll().stream().map(NeutralMerchant::getMerchantKey).collect(Collectors.toList());
    }

    public String addRule(String businessName) {
        String key = keyFor(businessName);
        if (key == null || key.isBlank()) {
            return null;
        }
        repository.save(NeutralMerchant.builder().merchantKey(key).build());
        return key;
    }

    public void removeRule(String key) {
        repository.deleteById(key == null ? "" : key.trim().toUpperCase());
    }
}
