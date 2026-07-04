package com.bishop.FinanceTracker.service.recurring;

import com.bishop.FinanceTracker.model.domain.CustomMerchant;
import com.bishop.FinanceTracker.model.recurring.MerchantClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Built-in merchant knowledge (ported from the front-end {@code merchantKnowledge.js})
 * plus user-defined custom rules. Produces a <em>prior</em> classification that the
 * engine blends into the confidence score — it never bypasses cadence detection
 * (feature doc §2.1/§2.5, fixing the v1 KB-bypass weakness).
 *
 * <p>Custom user patterns are compiled defensively: an invalid or absurdly long
 * regex is skipped rather than allowed to throw or hang (guards UI-N8 / ReDoS).
 */
@Slf4j
@Component
public class MerchantKnowledgeBase {

    /** User regex longer than this is ignored — cheap ReDoS backstop. */
    private static final int MAX_CUSTOM_PATTERN_LENGTH = 200;

    private static final int CI = Pattern.CASE_INSENSITIVE;

    private static final List<Pattern> SUBSCRIPTION = compileAll(
            "netflix", "spotify", "apple\\.com/bill|apple one|itunes|icloud",
            "disney[\\s+]?plus|disney\\+", "\\bstan\\b", "\\bbinge\\b",
            "paramount\\+|paramount plus", "amazon prime|prime video", "youtube premium",
            "\\baudible\\b", "kindle unlimited", "\\bhbo max\\b|max\\.com", "\\bpeacock\\b",
            "\\bhulu\\b", "\\btidal\\b", "\\bdeezer\\b", "soundcloud go",
            "microsoft 365|office 365", "adobe.*cloud|adobe creative", "\\bdropbox\\b",
            "google one|google storage|google workspace", "github copilot|github pro",
            "\\bnotion\\b", "\\b1password\\b", "\\blastpass\\b", "\\bdashlane\\b",
            "nordvpn|expressvpn|surfshark|mullvad", "canva pro", "\\bfigma\\b",
            "zoom pro|zoom subscription", "\\bmailchimp\\b", "\\bheadspace\\b", "\\bcalm\\b",
            "\\bstrava\\b", "myfitnesspal", "xbox.*game pass|game pass ultimate",
            "playstation.*plus|psn plus|ps plus", "nintendo.*online", "\\bea play\\b",
            "ubisoft connect", "new york times|nytimes", "the guardian", "washington post",
            "\\bmedium\\.com\\b", "\\bsubstack\\b", "\\bpearler\\b", "\\bsharesight\\b");

    private static final List<Pattern> BILL = compileAll(
            "\\brent\\b", "\\bmortgage\\b", "\\bbody corp\\b|\\bstrata\\b",
            "\\bcouncil rates?\\b|\\brates notice\\b",
            "home insurance|building insurance|contents insurance|landlord insurance",
            "\\borigin energy\\b", "\\bagl\\b", "energy australia", "\\bsynergy\\b",
            "\\bausgrid\\b", "\\bactew\\b|\\bicon water\\b", "sa power networks",
            "water.*corporation|water.*corp", "\\bpowercor\\b|\\bjemena\\b|\\bcitipower\\b",
            "\\bunited energy\\b", "\\btelstra\\b", "\\boptus\\b", "\\bvodafone\\b",
            "\\btpg\\b", "\\biinet\\b", "\\bspintel\\b", "\\bnbn\\b",
            "\\bmedibank\\b|\\bnib health\\b|\\bbupa\\b|\\bahm\\b",
            "\\baami\\b|\\bgio\\b|\\bbudget direct\\b|\\bnrma insurance\\b|\\bracq\\b|\\bracv\\b",
            "\\blife insurance\\b|\\bincome protection\\b", "\\bpet insurance\\b",
            "\\bdaycare\\b|\\bday care\\b|\\bchildcare\\b|\\bchild care\\b",
            "\\bkindergarten\\b|\\bkindy\\b|\\bnursery\\b",
            "\\bearly learning\\b|\\bearly childhood\\b", "\\bpre.?school\\b",
            "\\bafter school care\\b|\\boshc\\b", "\\bhome loan\\b|\\bpersonal loan\\b",
            "\\bcar.*repayment\\b|\\bcar.*loan\\b",
            "\\bcredit card.*payment\\b|\\bcard repayment\\b",
            "\\bsuperannuation\\b|\\bsuper.*contribution\\b");

    private static final List<Pattern> NOISE = compileAll(
            "\\bcoffee\\b|\\bcafe\\b|\\bcaffe\\b",
            "woolworths|coles|aldi|iga|harris farm|foodland|costco|spar",
            "mcdonald|hungry jack|\\bkfc\\b|subway|domino|pizza hut|red rooster|nandos|grill.?d",
            "\\bpetrol\\b|\\bservice station\\b|\\b7.?eleven\\b|\\bampol\\b|\\bbp\\b|\\bshell\\b|\\bcaltex\\b|\\bz energy\\b",
            "\\bparking\\b|\\bwilson parking\\b|\\bsecure parking\\b",
            "\\bbar\\b|\\bpub\\b|\\btavern\\b|\\bbottle.?o\\b|\\bbws\\b|\\bdan murphy");

    public static final int SEED_PATTERN_COUNT = SUBSCRIPTION.size() + BILL.size();

    /**
     * Classify a merchant. Custom rules win over the seed KB; the seed KB order is
     * noise → subscription → bill (matching the front-end precedence).
     */
    public MerchantClass classify(String merchantName, List<CustomMerchant> customMerchants) {
        if (merchantName == null || merchantName.isBlank()) {
            return MerchantClass.unknown();
        }

        if (customMerchants != null) {
            for (CustomMerchant cm : customMerchants) {
                Pattern p = safeCompile(cm.getMerchantPattern());
                if (p != null && p.matcher(merchantName).find()) {
                    MerchantClass.Type type = "bill".equalsIgnoreCase(cm.getMerchantType())
                            ? MerchantClass.Type.BILL
                            : "subscription".equalsIgnoreCase(cm.getMerchantType())
                                    ? MerchantClass.Type.SUBSCRIPTION
                                    : MerchantClass.Type.UNKNOWN;
                    if (type != MerchantClass.Type.UNKNOWN) {
                        return new MerchantClass(type, "custom");
                    }
                }
            }
        }

        if (matchesAny(NOISE, merchantName)) {
            return new MerchantClass(MerchantClass.Type.NOISE, "seed");
        }
        if (matchesAny(SUBSCRIPTION, merchantName)) {
            return new MerchantClass(MerchantClass.Type.SUBSCRIPTION, "seed");
        }
        if (matchesAny(BILL, merchantName)) {
            return new MerchantClass(MerchantClass.Type.BILL, "seed");
        }
        return MerchantClass.unknown();
    }

    private static boolean matchesAny(List<Pattern> patterns, String name) {
        for (Pattern p : patterns) {
            if (p.matcher(name).find()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern safeCompile(String pattern) {
        if (pattern == null || pattern.isBlank() || pattern.length() > MAX_CUSTOM_PATTERN_LENGTH) {
            return null;
        }
        try {
            return Pattern.compile(pattern, CI);
        } catch (RuntimeException e) {
            log.warn("Skipping invalid custom merchant pattern '{}': {}", pattern, e.getMessage());
            return null;
        }
    }

    private static List<Pattern> compileAll(String... regexes) {
        return Arrays.stream(regexes).map(r -> Pattern.compile(r, CI)).toList();
    }
}
