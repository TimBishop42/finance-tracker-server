package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.recurring.NormalizedMerchant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicMerchantNormalizerTest {

    private final HeuristicMerchantNormalizer normalizer = new HeuristicMerchantNormalizer();

    private String key(String raw) {
        return normalizer.normalize(raw).key();
    }

    @Test
    void collapsesProcessorPrefixDomainAndCaseToOneKey() {
        String expected = key("Netflix");
        assertEquals(expected, key("NETFLIX.COM"));
        assertEquals(expected, key("SQ *NETFLIX"));
        assertEquals(expected, key("  netflix  "));
        assertEquals("NETFLIX", expected);
    }

    @Test
    void stripsStoreNumbersDatesAndCardTails() {
        assertEquals("WOOLWORTHS", key("WOOLWORTHS 4001"));
        assertEquals("WOOLWORTHS", key("WOOLWORTHS 12/03 XXXX1234"));
    }

    @Test
    void stripsBankingPrefixes() {
        assertEquals("SPOTIFY", key("DIRECT DEBIT SPOTIFY"));
        assertEquals("SPOTIFY", key("VISA PURCHASE SPOTIFY"));
    }

    @Test
    void stripsCompanySuffixes() {
        assertEquals(key("ACME"), key("ACME PTY LTD"));
        assertEquals(key("ACME"), key("Acme Limited"));
    }

    @Test
    void producesDisplayName() {
        NormalizedMerchant nm = normalizer.normalize("SQ *NETFLIX.COM");
        assertEquals("Netflix", nm.displayName());
    }

    @Test
    void blankInputYieldsBlankKey() {
        assertTrue(key("").isBlank());
        assertTrue(key(null).isBlank());
    }
}
