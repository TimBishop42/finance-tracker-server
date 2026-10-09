package com.bishop.FinanceTracker.service;

import com.bishop.FinanceTracker.model.domain.Category;
import com.bishop.FinanceTracker.model.domain.Subscription;
import com.bishop.FinanceTracker.model.domain.SubscriptionPriceHistory;
import com.bishop.FinanceTracker.model.domain.Transaction;
import com.bishop.FinanceTracker.model.json.MonthlyReviewResponse;
import com.bishop.FinanceTracker.model.json.MonthlyReviewResponse.CategoryRow;
import com.bishop.FinanceTracker.model.json.MonthlyReviewResponse.MerchantRow;
import com.bishop.FinanceTracker.model.json.MonthlyReviewResponse.Trend;
import com.bishop.FinanceTracker.repository.SubscriptionPriceHistoryRepository;
import com.bishop.FinanceTracker.repository.SubscriptionRepository;
import com.bishop.FinanceTracker.service.recurring.HeuristicMerchantNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyReviewServiceTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);

    private final List<Transaction> txs = new ArrayList<>();
    private MonthlyReviewService service;
    private SubscriptionRepository subscriptions;
    private SubscriptionPriceHistoryRepository priceHistory;

    @BeforeEach
    void setup() {
        // Groceries flat at 100 then jumps to 200 in Sep (UP); Dining falls 300 -> 100 (DOWN).
        expense("01-06-2026", "Groceries", "WOOLWORTHS 1234 SYDNEY", "100");
        expense("01-07-2026", "Groceries", "WOOLWORTHS 1234 SYDNEY", "100");
        expense("01-08-2026", "Groceries", "WOOLWORTHS 1234 SYDNEY", "100");
        expense("05-09-2026", "Groceries", "WOOLWORTHS 1234 SYDNEY", "150");
        expense("20-09-2026", "Groceries", "NEW CAFE", "50");
        expense("02-06-2026", "Dining", "OLD BISTRO", "300");
        expense("02-07-2026", "Dining", "OLD BISTRO", "200");
        expense("02-08-2026", "Dining", "OLD BISTRO", "150");
        expense("02-09-2026", "Dining", "OLD BISTRO", "100");
        txs.add(tx("15-09-2026", "Salary", "EMPLOYER", "5000", "INCOME"));
        txs.add(tx("15-08-2026", "Salary", "EMPLOYER", "4000", "INCOME"));
        // Neutral transfers never count as spend, but do count toward dataThrough.
        txs.add(tx("03-10-2026", "Transfer", "CARD PAYMENT", "9999", "NEUTRAL"));

        TransactionService transactionService = mock(TransactionService.class);
        when(transactionService.getAll()).thenReturn(txs);
        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.getAllCategories()).thenReturn(List.of(
                Category.builder().categoryName("Groceries").monthlyBudget(new BigDecimal("180")).build(),
                Category.builder().categoryName("Fuel").monthlyBudget(new BigDecimal("150")).build(),
                Category.builder().categoryName("Dining").build()));
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.getMaxSpendValue()).thenReturn(new BigDecimal("12000"));
        subscriptions = mock(SubscriptionRepository.class);
        priceHistory = mock(SubscriptionPriceHistoryRepository.class);

        service = new MonthlyReviewService(transactionService,
                new AggregationService(transactionService, categoryService, settings), categoryService, settings,
                new HeuristicMerchantNormalizer(), subscriptions, priceHistory);
    }

    @Test
    void totalsCompareAgainstLastMonthAndAverageOfPriorMonths() {
        MonthlyReviewResponse.Totals t = service.review(SEP).totals();

        assertMoney("300", t.spend());
        assertMoney("5000", t.income());
        assertMoney("4700", t.net());
        assertMoney("94.0", t.savingsRatePercent());
        assertMoney("250", t.lastMonthSpend());
        assertMoney("4000", t.lastMonthIncome());
        // History starts in June, so only Jun-Aug are averaged: (400 + 300 + 250) / 3.
        assertEquals(3, t.averageMonths());
        assertMoney("316.67", t.averageSpend());
        assertMoney("1333.33", t.averageIncome());
    }

    @Test
    void categoriesCarryBudgetLastMonthAverageAndTrend() {
        MonthlyReviewResponse review = service.review(SEP);

        CategoryRow groceries = category(review, "Groceries");
        assertMoney("200", groceries.spend());
        assertMoney("180", groceries.budget());
        assertMoney("100", groceries.lastMonth());
        assertMoney("100.00", groceries.average());
        assertEquals(Trend.UP, groceries.trend());
        assertEquals(4, groceries.history().size());

        CategoryRow dining = category(review, "Dining");
        assertNull(dining.budget());
        assertEquals(Trend.DOWN, dining.trend());

        // Budgeted but unspent categories still appear, so the budget is visible.
        CategoryRow fuel = category(review, "Fuel");
        assertMoney("0", fuel.spend());
        assertEquals(Trend.FLAT, fuel.trend());

        assertEquals("Groceries", review.categories().get(0).category(), "sorted by spend, largest first");
    }

    @Test
    void merchantsAreGroupedByNormalisedNameAndNewOnesFlagged() {
        MonthlyReviewResponse review = service.review(SEP);

        assertEquals(List.of("Woolworths Sydney", "Old Bistro", "New Cafe"),
                review.topMerchants().stream().map(MerchantRow::merchant).toList());
        assertEquals(List.of("New Cafe"), review.newMerchants().stream().map(MerchantRow::merchant).toList());
        assertMoney("150", review.biggestTransactions().get(0).amount());
        assertEquals(LocalDate.of(2026, 10, 3), review.dataThrough());
    }

    @Test
    void cumulativeSpendRunsAcrossEveryDayOfBothMonths() {
        MonthlyReviewResponse.Cumulative c = service.review(SEP).cumulative();

        assertEquals(30, c.thisMonth().size());
        assertEquals(31, c.lastMonth().size());
        assertMoney("0", c.thisMonth().get(0));
        assertMoney("100", c.thisMonth().get(1));
        assertMoney("300", c.thisMonth().get(29));
    }

    @Test
    void subscriptionChangesInTheMonthAreReportedAndCancelledOnesIgnored() {
        when(subscriptions.findAll()).thenReturn(List.of(
                sub(1L, "Netflix", "active", "2025-01-10,2026-09-10", null),
                sub(2L, "Gym", "active", "2026-09-04,2026-10-04", null),
                sub(3L, "Disney", "active", null, "2026-09-21"),
                sub(4L, "Old Mag", "cancelled", "2026-09-01", null)));
        when(priceHistory.findAll()).thenReturn(List.of(
                price(1L, "2025-01-10", "18.99"),
                price(1L, "2026-09-10", "22.99"),
                price(4L, "2026-01-01", "5"),
                price(4L, "2026-09-01", "6")));

        MonthlyReviewResponse.SubscriptionChanges changes = service.review(SEP).subscriptionChanges();

        assertEquals(List.of("Gym"), changes.started().stream().map(MonthlyReviewResponse.CommitmentRow::name).toList());
        assertEquals(1, changes.priceChanges().size());
        assertMoney("18.99", changes.priceChanges().get(0).oldAmount());
        assertMoney("22.99", changes.priceChanges().get(0).newAmount());
        assertEquals(List.of("Disney"), changes.trialsEnded().stream().map(MonthlyReviewResponse.CommitmentRow::name).toList());
    }

    @Test
    void monthWithNoHistoryHasNoAveragesOrTrend() {
        MonthlyReviewResponse review = service.review(YearMonth.of(2026, 6));

        assertEquals(0, review.totals().averageMonths());
        assertNull(review.totals().averageSpend());
        assertNull(category(review, "Groceries").trend());
    }

    @Test
    void unparseableDateRowIsSkippedNotFatal() {
        txs.add(tx("2026/09/30", "Groceries", "BAD ROW", "1000", "EXPENSE"));

        assertMoney("300", service.review(SEP).totals().spend());
    }

    @Test
    void trendIsRelativeToTheThreeMonthMean() {
        assertNull(MonthlyReviewService.trend(List.of(BigDecimal.ONE, BigDecimal.TEN)));
        assertEquals(Trend.FLAT, MonthlyReviewService.trend(List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        // (105 - 100) * 3 / (2 * 305) = 0.0246 -> within the 10% band.
        assertEquals(Trend.FLAT, MonthlyReviewService.trend(money("100", "100", "105")));
        assertEquals(Trend.UP, MonthlyReviewService.trend(money("0", "0", "10")));
    }

    private void expense(String date, String category, String merchant, String amount) {
        txs.add(tx(date, category, merchant, amount, "EXPENSE"));
    }

    private static Transaction tx(String date, String category, String merchant, String amount, String type) {
        return Transaction.builder().transactionDate(date).category(category).businessName(merchant)
                .amount(new BigDecimal(amount)).transactionType(type).build();
    }

    private static Subscription sub(Long id, String name, String status, String paidDates, String trialEnd) {
        return Subscription.builder().id(id).name(name).status(status).paidDates(paidDates)
                .trialEndDate(trialEnd).amount(BigDecimal.TEN).billingCycle("monthly").build();
    }

    private static SubscriptionPriceHistory price(Long subscriptionId, String date, String amount) {
        return SubscriptionPriceHistory.builder().subscriptionId(subscriptionId).effectiveDate(date)
                .amount(new BigDecimal(amount)).build();
    }

    private static CategoryRow category(MonthlyReviewResponse review, String name) {
        return review.categories().stream().filter(c -> c.category().equals(name)).findFirst().orElseThrow();
    }

    private static List<BigDecimal> money(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
