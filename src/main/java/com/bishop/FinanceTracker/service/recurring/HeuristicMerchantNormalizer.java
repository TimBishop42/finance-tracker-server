package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.recurring.NormalizedMerchant;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Regex/heuristic merchant normalisation (feature doc §2.1, step 1). Strips the
 * bank/card cruft that made v1's exact-match grouping unreliable (UI-N9) so that
 * "SQ *NETFLIX.COM 4001", "Netflix" and "NETFLIX.COM" all collapse to one key.
 *
 * <p>Order matters: processor/banking prefixes first, then domains, dates, card
 * tails and reference/store numbers, then company suffixes, then punctuation and
 * whitespace. Deliberately conservative — it cleans noise but does not merge on
 * a single leading token, so distinct products aren't wrongly pooled.
 */
@Component
public class HeuristicMerchantNormalizer implements MerchantNormalizer {

    // Leading banking / gateway noise, e.g. "POS ", "VISA PURCHASE ", "DIRECT DEBIT ".
    private static final Pattern BANKING_PREFIX = Pattern.compile(
            "^(?:pos|eftpos|visa(?:\\s+purchase)?|mastercard|debit\\s+card(?:\\s+purchase)?|"
                    + "card\\s+purchase|direct\\s+debit|dd|osko(?:\\s+payment)?|payid|bpay|"
                    + "purchase|payment\\s+to|transfer\\s+to)\\b[\\s:.-]*",
            Pattern.CASE_INSENSITIVE);

    // Card-processor prefixes: "SQ *", "PAYPAL *", "SP. ", "TST* ", "DLOCAL*".
    private static final Pattern PROCESSOR_PREFIX = Pattern.compile(
            "^(?:sq|sp|pp|paypal|tst|dd|dlocal|stripe|zip|afterpay|klarna|humm)\\s*[*.]\\s*",
            Pattern.CASE_INSENSITIVE);

    // "www." and TLD suffixes on inline domains ("netflix.com.au" -> "netflix").
    private static final Pattern WWW = Pattern.compile("\\bwww\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern TLD = Pattern.compile(
            "\\.(?:com|net|org|io|co|app|xyz|gg)(?:\\.au|\\.uk|\\.nz|\\.us)?\\b",
            Pattern.CASE_INSENSITIVE);

    // Company suffixes.
    private static final Pattern SUFFIX = Pattern.compile(
            "\\b(?:pty\\.?\\s*ltd\\.?|ltd\\.?|limited|inc\\.?|llc|corp\\.?|incorporated|"
                    + "holdings|group|australia|aus)\\b",
            Pattern.CASE_INSENSITIVE);

    // Dates ("12/03", "2024-01-05"), card tails ("XXXX1234", "x4321").
    private static final Pattern DATE = Pattern.compile(
            "\\b\\d{1,4}[/-]\\d{1,2}(?:[/-]\\d{1,4})?\\b");
    private static final Pattern CARD_TAIL = Pattern.compile("\\bx{2,}\\d+\\b", Pattern.CASE_INSENSITIVE);

    // Standalone store / location / reference numbers (3+ digits). Kept short
    // digit runs so "7 eleven" survives; multi-digit product names like
    // "microsoft 365" lose the number, which is fine for grouping.
    private static final Pattern REF_NUMBER = Pattern.compile("\\b[a-z]*\\d{3,}[a-z0-9]*\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern MULTISPACE = Pattern.compile("\\s+");

    @Override
    public NormalizedMerchant normalize(String rawBusinessName) {
        String raw = rawBusinessName == null ? "" : rawBusinessName.trim();
        if (raw.isEmpty()) {
            return new NormalizedMerchant("", "");
        }

        String s = raw.toLowerCase();
        s = BANKING_PREFIX.matcher(s).replaceFirst("");
        s = PROCESSOR_PREFIX.matcher(s).replaceFirst("");
        // A second pass catches stacked prefixes like "POS SQ *MERCHANT".
        s = PROCESSOR_PREFIX.matcher(s).replaceFirst("");
        s = WWW.matcher(s).replaceAll(" ");
        s = TLD.matcher(s).replaceAll(" ");
        s = CARD_TAIL.matcher(s).replaceAll(" ");
        s = DATE.matcher(s).replaceAll(" ");
        s = REF_NUMBER.matcher(s).replaceAll(" ");
        s = SUFFIX.matcher(s).replaceAll(" ");
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        s = MULTISPACE.matcher(s).replaceAll(" ").trim();

        if (s.isEmpty()) {
            // Everything got stripped (e.g. name was pure digits) — fall back to a
            // cleaned version of the original so the group still exists.
            s = NON_ALNUM.matcher(raw.toLowerCase()).replaceAll(" ").trim();
            s = MULTISPACE.matcher(s).replaceAll(" ");
        }

        String key = s.toUpperCase();
        return new NormalizedMerchant(key, titleCase(s));
    }

    private static String titleCase(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return Arrays.stream(s.split(" "))
                .filter(w -> !w.isBlank())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}
